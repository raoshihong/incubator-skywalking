/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.agent;

import java.lang.instrument.Instrumentation;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.apache.skywalking.apm.agent.core.plugin.PluginBootstrap;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.PluginFinder;
import org.apache.skywalking.apm.agent.core.conf.SnifferConfigInitializer;

/**
 * The main entrance of sky-waking agent,
 * based on javaagent mechanism.
 * sky-walking 代理的主要入口，基于javaagent机制实现。
 * @author wusheng
 */
public class SkyWalkingAgent {
    private static final ILog logger = LogManager.getLogger(SkyWalkingAgent.class);

    /**
     * Main entrance.
     * 主要入口
     * Use byte-buddy transform to enhance all classes, which define in plugins.
     *  使用byte-buddy字节码技术来增强在插件中定义的所有类。
     * @param agentArgs
     * @param instrumentation 仪表器对象
     * @throws PluginException
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        final PluginFinder pluginFinder;
        try {
            //初始化配置,加载/config/agent.config和System.env中的属性配置
            SnifferConfigInitializer.initialize();//调试的时候,需要将config/agent.config拷贝到与agent.jar同目录去,不然就得该路径

            //创建插件扩展器,这是SkyWalking提供对外插件的扩展,这些插件可以在apm-sdk-plugin模块中看到,想要那个插件就添加那个插件
            /**
             * skywalking有一套自己的的插件定义体系,请看apm-sniffer下的pluginREADME.md
             */
            pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());

        } catch (Exception e) {
            logger.error(e, "Skywalking agent initialized failure. Shutting down.");
            return;
        }

        //通过byte-buddy 字节码技术创建代理类,为什么使用的是Byte-buddy,因为byte-buddy中恰好又有javaAgent的代理类
        new AgentBuilder.Default()//使用默认的代理构建
                .type(pluginFinder.buildMatch())//通过插件扩展的方式来指明要代理拦截哪些类,这里使用buildMatch表示匹配的路径的类就会被拦截
                .transform(new Transformer(pluginFinder))//通过插件扩展的方式指明自定义的拦截器和拦截的方法名,表示哪些方法需要进行增强处理
                .with(new Listener())//使用监听器
                .installOn(instrumentation);//调用installOn时,就是将ClassFileTransformer添加到Instrumentation中,我们在使用java探针技术时,就有这么一句inst.addTransformer(new MyTransformer());

        /**
         * installOn方法关键有两步
         * ResettableClassFileTransformer classFileTransformer = makeRaw(installation.getListener(), installation.getInstallationListener());
         * instrumentation.addTransformer(classFileTransformer, redefinitionStrategy.isRetransforming());
         */

        try {
            //利用枚举的方式创建了一个ServiceManager单利(666),ServiceManager用来管理BootService
            ServiceManager.INSTANCE.boot();//比如;@DefaultImplementor  public class CollectorDiscoveryService implements BootService 启动这个服务去发现apm-collector服务
        } catch (Exception e) {
            logger.error(e, "Skywalking agent boot failure.");
        }

        //添加钩子线程,优雅关闭所有的BootService
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override public void run() {
                ServiceManager.INSTANCE.shutdown();
            }
        }, "skywalking service shutdown thread"));
    }

    //类转变期,实际上调用的就是Instrument中的ClassFileTransformer

    /**
     * SkyWalking自定义了自己的Transformer,实现AgentBuilder.Transformer
     * Transformer的transform方法会在ClassFileTransform的transform方法调用后被调用,从而对目标方法进行增加添加拦截器
     * 在这个Transform中指定了拦截器和拦截器的方法
     * 通过PluginFinder查找到各个apm-sdk-plugin中自定义插件中实现的类实例方法增强ClassInstanceMethodsEnhancePluginDefine插件定义和拦截器
     */
    private static class Transformer implements AgentBuilder.Transformer {
        private PluginFinder pluginFinder;

        Transformer(PluginFinder pluginFinder) {
            this.pluginFinder = pluginFinder;
        }


        /**
         * 作用:指定拦截目标方法和绑定拦截器
         *
         * 这个方法在调用在ClassFileLoad事件出发调用ClassFileTransform.transform时被调用
         *
         * skywalking利用这个方法,实现了插件体系,通过自定义插件的形式,获取插件中定义的拦截器和拦截定义类(指定拦截器和要拦截的哪个目标方法 )
         *
         * @param builder
         * @param typeDescription
         * @param classLoader
         * @param module
         * @return
         */
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
            //获取加载的类对应类型的增加插件类
            List<AbstractClassEnhancePluginDefine> pluginDefines = pluginFinder.find(typeDescription, classLoader);
            if (pluginDefines.size() > 0) {
                DynamicType.Builder<?> newBuilder = builder;
                EnhanceContext context = new EnhanceContext();
                //获取每个插件的定义
                for (AbstractClassEnhancePluginDefine define : pluginDefines) {
                    //在这里调用define,对目标类进行增强,委托给其他类进行处理
                    //在define方法中有两个主要步骤：
                    // 1.解析了自定义插件中的拦截器类和要拦截的目标方法,
                    // 2.并通过byte-buddy中的AgentBudiler绑定了自定义的插件的拦截器

                    //最终返回一个目标类的代理类实例
                    DynamicType.Builder<?> possibleNewBuilder = define.define(typeDescription.getTypeName(), newBuilder, classLoader, context);
                    if (possibleNewBuilder != null) {
                        newBuilder = possibleNewBuilder;
                    }
                }
                if (context.isEnhanced()) {
                    logger.debug("Finish the prepare stage for {}.", typeDescription.getName());
                }

                return newBuilder;
            }

            logger.debug("Matched class {}, but ignore by finding mechanism.", typeDescription.getTypeName());
            return builder;
        }
    }

    /**
     * 增强处理的监听
     */
    private static class Listener implements AgentBuilder.Listener {
        @Override
        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {

        }

        /**
         * 当调用transform修改成功时,调用这个方法
         * @param typeDescription
         * @param classLoader
         * @param module
         * @param loaded
         * @param dynamicType
         */
        @Override
        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                                     boolean loaded, DynamicType dynamicType) {
            if (logger.isDebugEnable()) {
                logger.debug("On Transformation class {}.", typeDescription.getName());
            }
            //将成功代理的类保存
            InstrumentDebuggingClass.INSTANCE.log(typeDescription, dynamicType);
        }

        @Override
        public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                              boolean loaded) {

        }

        @Override
        public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded,
                            Throwable throwable) {
            logger.error("Enhance class " + typeName + " error.", throwable);
        }

        @Override
        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
        }
    }
}
