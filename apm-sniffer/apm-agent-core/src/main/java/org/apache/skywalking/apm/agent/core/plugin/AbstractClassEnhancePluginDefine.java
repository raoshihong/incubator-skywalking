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

import net.bytebuddy.dynamic.DynamicType;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * Basic abstract class of all sky-walking auto-instrumentation plugins.
 * 所有sky-walking instrumentation插件的基类
 * <p>
 * It provides the outline of enhancing the target class.
 * 它提供了增强目标类的概述
 * If you want to know more about enhancing, you should go to see {@link ClassEnhancePluginDefine}
 * 如果您想了解有关增强的更多信息，请访问{@link ClassEnhancePluginDefine}
 *
 * ClassEnhancePluginDefine 这个子类实现了更加具体的增强
 *
 * 不同的插件实现这个基类,来定义不同框架的切面，记录调用链路
 */
public abstract class AbstractClassEnhancePluginDefine {
    private static final ILog logger = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);

    /**
     * Main entrance of enhancing the class.
     * 类增强器的主要入口
     * @param transformClassName target class. 目标方法
     * @param builder byte-buddy's builder to manipulate target class's bytecode. byte-buddy对象
     * @param classLoader load the given transformClass
     * @return the new builder, or <code>null</code> if not be enhanced.
     * @throws PluginException when set builder failure.
     */
    public DynamicType.Builder<?> define(String transformClassName,
        DynamicType.Builder<?> builder, ClassLoader classLoader, EnhanceContext context) throws PluginException {
        //
        String interceptorDefineClassName = this.getClass().getName();

        if (StringUtil.isEmpty(transformClassName)) {
            logger.warn("classname of being intercepted is not defined by {}.", interceptorDefineClassName);
            return null;
        }

        logger.debug("prepare to enhance class {} by {}.", transformClassName, interceptorDefineClassName);

        /**
         * find witness classes for enhance class
         * 找到增加类的witness classes
         */
        String[] witnessClasses = witnessClasses();
        if (witnessClasses != null) {
            for (String witnessClass : witnessClasses) {
                if (!WitnessClassFinder.INSTANCE.exist(witnessClass, classLoader)) {
                    logger.warn("enhance class {} by plugin {} is not working. Because witness class {} is not existed.", transformClassName, interceptorDefineClassName,
                        witnessClass);
                    return null;
                }
            }
        }

        /**
         * find origin class source code for interceptor
         */
        //这里通过子类实现enhance方法来返回增强后的类DynamicType实例,而通过newClassBuilder.make().load().getLoaded()就可以返回增强类的Class实例,进而可以通过反射进行调用
        //这里的enhance可以查看
        DynamicType.Builder<?> newClassBuilder = this.enhance(transformClassName, builder, classLoader, context);

        context.initializationStageCompleted();
        logger.debug("enhance class {} by {} completely.", transformClassName, interceptorDefineClassName);

        return newClassBuilder;
    }

    /**
     * 定义增加方法,子类实现
     * @param enhanceOriginClassName
     * @param newClassBuilder
     * @param classLoader
     * @param context
     * @return
     * @throws PluginException
     */
    protected abstract DynamicType.Builder<?> enhance(String enhanceOriginClassName,
        DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader, EnhanceContext context) throws PluginException;

    /**
     * Define the {@link ClassMatch} for filtering class.
     *
     * @return {@link ClassMatch}
     */
    protected abstract ClassMatch enhanceClass();

    /**
     * Witness classname list. Why need witness classname? Let's see like this: A library existed two released versions
     * (like 1.0, 2.0), which include the same target classes, but because of version iterator, they may have the same
     * name, but different methods, or different method arguments list. So, if I want to target the particular version
     * (let's say 1.0 for example), version number is obvious not an option, this is the moment you need "Witness
     * classes". You can add any classes only in this particular release version ( something like class
     * com.company.1.x.A, only in 1.0 ), and you can achieve the goal.
     *
     * @return
     */
    protected String[] witnessClasses() {
        return new String[] {};
    }
}
