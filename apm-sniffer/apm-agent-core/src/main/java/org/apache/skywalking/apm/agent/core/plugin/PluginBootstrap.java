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


package org.apache.skywalking.apm.agent.core.plugin;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * Plugins finder.
 * 插件查找器。
 * Use {@link PluginResourcesResolver} to find all plugins,
 * and ask {@link PluginCfg} to load all plugin definitions.
 *
 * 使用{@link PluginResourcesResolver}查找所有插件，并要求{@link PluginCfg}加载所有插件定义
 *
 * @author wusheng
 */
public class PluginBootstrap {
    private static final ILog logger = LogManager.getLogger(PluginBootstrap.class);

    /**
     * load all plugins.
     * 加载所有的插件,并返回所有插件的实例对象
     * @return plugin definition list.
     */
    public List<AbstractClassEnhancePluginDefine> loadPlugins() throws AgentPackageNotFoundException {
        //初始化自定义的类加载器
        AgentClassLoader.initDefaultLoader();

        //创建插件资源解析器
        PluginResourcesResolver resolver = new PluginResourcesResolver();
        //获取所有插件的skywalking-plugin.def文件路径
        List<URL> resources = resolver.getResources();

        if (resources == null || resources.size() == 0) {
            logger.info("no plugin files (skywalking-plugin.def) found, continue to start application.");
            return new ArrayList<AbstractClassEnhancePluginDefine>();
        }

        //遍历所有skywalking-plugin.def文件,将文件内容转化为PluginDefine保存到内存中
        for (URL pluginUrl : resources) {
            try {
                //解析skywalking-plugin.def文件内容
                PluginCfg.INSTANCE.load(pluginUrl.openStream());
            } catch (Throwable t) {
                logger.error(t, "plugin file [{}] init failure.", pluginUrl);
            }
        }

        //获取所有解析的插件定义dubbo=org.apache.skywalking.apm.plugin.dubbo.DubboInstrumentation  这就是一个PluginDefine对象
        List<PluginDefine> pluginClassList = PluginCfg.INSTANCE.getPluginClassList();

        List<AbstractClassEnhancePluginDefine> plugins = new ArrayList<AbstractClassEnhancePluginDefine>();
        for (PluginDefine pluginDefine : pluginClassList) {
            try {
                logger.debug("loading plugin class {}.", pluginDefine.getDefineClass());
                //根据加载的插件的类的全路径，返回插件的实例对象
                //通过反射创建插件的实例,从这里可以看出,所有的插件都必须实现这个类
                AbstractClassEnhancePluginDefine plugin =
                    (AbstractClassEnhancePluginDefine)Class.forName(pluginDefine.getDefineClass(),
                        true,
                        AgentClassLoader.getDefault())
                        .newInstance();
                plugins.add(plugin);
            } catch (Throwable t) {
                logger.error(t, "load plugin [{}] failure.", pluginDefine.getDefineClass());
            }
        }

        return plugins;

    }

}
