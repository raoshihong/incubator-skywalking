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

package org.apache.skywalking.apm.plugin.dubbo;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * skywalking的插件的定义有以下几点
 * 1.定义指明要拦截的类,通过enhanceClass返回
 * 2.定义要拦截静态方法对应的拦截器以及要拦截的方法，见getStaticMethodsInterceptPoints()方法
 * 2.定义拦截构造方法对应的拦截器以及要拦截的方法 ，见getConstructorsInterceptPoints()方法
 * 3.定义拦截实例方法对应的拦截器以及要拦截的方法，见getInstanceMethodsInterceptPoints()方法
 */
public class DubboInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.alibaba.dubbo.monitor.support.MonitorFilter";
    private static final String INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.dubbo.DubboInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        //自定义要匹配的目标类,指明要代理这个类
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                //定义匹配(拦截)规则，即要拦截目标类的哪个方法
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("invoke");//表示匹配到方法名为invoke,即拦截方法invoke
                }

                //定义拦截器
                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_CLASS;
                }

                //指明是否要覆盖参数
                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
