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


package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.lang.reflect.Method;

/**
 * A interceptor, which intercept method's invocation. The target methods will be defined in {@link
 * ClassEnhancePluginDefine}'s subclass, most likely in {@link ClassInstanceMethodsEnhancePluginDefine}
 * 拦截器，拦截方法的调用。 目标方法将在{@link ClassEnhancePluginDefine}的子类中定义，最有可能在{@link ClassInstanceMethodsEnhancePluginDefine}中定义
 *
 * skywalking插件增强代理拦截器的基类,在插件中定义的拦截器都需要实现这个接口
 *
 * @author wusheng
 */
public interface InstanceMethodsAroundInterceptor {
    /**
     * called before target method invocation.
     *  目标方法调用之前拦截调用
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable;

    /**
     * called after target method invocation. Even method's invocation triggers an exception.
     * 在目标方法调用之后调用。 甚至方法的调用也会触发异常。
     * @param method
     * @param ret the method's original return value.
     * @return the method's actual return value.
     * @throws Throwable
     */
    Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable;

    /**
     * called when occur exception.
     * 目标方法抛出异常时调用
     * @param method
     * @param t the exception occur.
     */
    void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes,
        Throwable t);
}
