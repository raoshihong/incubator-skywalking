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

import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.Morph;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.EnhanceException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.StringUtil;

import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * This class controls all enhance operations, including enhance constructors, instance methods and static methods. All
 * the enhances base on three types interceptor point: {@link ConstructorInterceptPoint}, {@link
 * InstanceMethodsInterceptPoint} and {@link StaticMethodsInterceptPoint} If plugin is going to enhance constructors,
 * instance methods, or both, {@link ClassEnhancePluginDefine} will add a field of {@link
 * Object} type.
 *
 *
 * 此类控制所有增强操作，包括增强构造函数，实例方法和静态方法。
 * 所有增强基于三种类型的拦截点：{@ link ConstructorInterceptPoint}，{@ link InstanceMethodsInterceptPoint}和{@link StaticMethodsInterceptPoint}
 * 如果插件要增强构造函数，实例方法或两者，{@link ClassEnhancePluginDefine}将添加一个字段 @@link Object}类型。
 *
 * 所以子类只需要实现ConstructorInterceptPoint或者InstanceMethodsInterceptPoint或者StaticMethodsInterceptPoint这三个方法即可实现插件增强
 *
 * @author wusheng
 */
public abstract class ClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
    private static final ILog logger = LogManager.getLogger(ClassEnhancePluginDefine.class);

    /**
     * New field name.
     */
    public static final String CONTEXT_ATTR_NAME = "_$EnhancedClassField_ws";

    /**
     * Begin to define how to enhance class.
     * 开始定义如何增强类。
     * After invoke this method, only means definition is finished.
     * 调用此方法后，仅表示定义已完成。
     * @param enhanceOriginClassName target class name 目标类
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode. 增加后的
     * @return new byte-buddy's builder for further manipulation.
     *
     * 通过这个方法将原始类通过byte-buddy字节码技术增强后返回DynamicType.Builder对象
     */
    @Override
    protected DynamicType.Builder<?> enhance(String enhanceOriginClassName,
        DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader,
        EnhanceContext context) throws PluginException {
        newClassBuilder = this.enhanceClass(enhanceOriginClassName, newClassBuilder, classLoader);

        newClassBuilder = this.enhanceInstance(enhanceOriginClassName, newClassBuilder, classLoader, context);

        return newClassBuilder;
    }

    /**
     * Enhance a class to intercept constructors and class instance methods.
     *
     * 增强类以拦截构造函数和类实例方法。
     *
     * @param enhanceOriginClassName target class name
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    private DynamicType.Builder<?> enhanceInstance(String enhanceOriginClassName,
        DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader,
        EnhanceContext context) throws PluginException {

        //调用子类实现的构造方法拦截器方法,比如dubbo-plugin的DubboInstrumentation.getConstructorsInterceptPoints()方法
        ConstructorInterceptPoint[] constructorInterceptPoints = getConstructorsInterceptPoints();
        //调用子类实现的构造方法拦截器方法,比如dubbo-plugin的DubboInstrumentation.getInstanceMethodsInterceptPoints()方法

        //在这些方法中定义要拦截的哪些方法的名称
        InstanceMethodsInterceptPoint[] instanceMethodsInterceptPoints = getInstanceMethodsInterceptPoints();

        boolean existedConstructorInterceptPoint = false;
        if (constructorInterceptPoints != null && constructorInterceptPoints.length > 0) {
            existedConstructorInterceptPoint = true;
        }
        boolean existedMethodsInterceptPoints = false;
        if (instanceMethodsInterceptPoints != null && instanceMethodsInterceptPoints.length > 0) {
            existedMethodsInterceptPoints = true;
        }

        /**
         * nothing need to be enhanced in class instance, maybe need enhance static methods.
         * 如果子类都没实现,则直接返回默认的增强对象
         */
        if (!existedConstructorInterceptPoint && !existedMethodsInterceptPoints) {
            return newClassBuilder;
        }

        /**
         * Manipulate class source code.<br/>
         *
         * new class need:<br/>
         * 1.Add field, name {@link #CONTEXT_ATTR_NAME}.
         * 2.Add a field accessor for this field.
         *
         * And make sure the source codes manipulation only occurs once.
         *
         */
        if (!context.isObjectExtended()) {
            //添加一个属性
            newClassBuilder = newClassBuilder.defineField(CONTEXT_ATTR_NAME, Object.class, ACC_PRIVATE)
                .implement(EnhancedInstance.class)
                .intercept(FieldAccessor.ofField(CONTEXT_ATTR_NAME));//使用FieldAccessor拦截器
            context.extendObjectCompleted();
        }

        /**
         * 2. enhance constructors
         * 构造方法的增强
         */
        if (existedConstructorInterceptPoint) {
            for (ConstructorInterceptPoint constructorInterceptPoint : constructorInterceptPoints) {
                //在这里对构造方法添加拦截器,使用constructorInterceptPoint.getConstructorMatcher()获取拦截器实现类
                newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher()).intercept(SuperMethodCall.INSTANCE
                    .andThen(MethodDelegation.withDefaultConfiguration()
                        .to(new ConstructorInter(constructorInterceptPoint.getConstructorInterceptor(), classLoader))
                    )
                );
            }
        }

        /**
         * 3. enhance instance methods
         * 实例方法的增强
         */
        if (existedMethodsInterceptPoints) {
            for (InstanceMethodsInterceptPoint instanceMethodsInterceptPoint : instanceMethodsInterceptPoints) {
                //获取拦截器实现类的名称
                String interceptor = instanceMethodsInterceptPoint.getMethodsInterceptor();
                if (StringUtil.isEmpty(interceptor)) {
                    throw new EnhanceException("no InstanceMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
                }


                //下面是通过byte-buddy指定拦截器实现类,这样在调用目标方法时就会执行拦截器的方法
                if (instanceMethodsInterceptPoint.isOverrideArgs()) {
                    newClassBuilder =
                        newClassBuilder.method(not(isStatic()).and(instanceMethodsInterceptPoint.getMethodsMatcher()))
                            .intercept(
                                MethodDelegation.withDefaultConfiguration()
                                    .withBinders(
                                        Morph.Binder.install(OverrideCallable.class)
                                    )
                                    .to(new InstMethodsInterWithOverrideArgs(interceptor, classLoader))
                            );
                } else {
                    newClassBuilder =
                        newClassBuilder.method(not(isStatic()).and(instanceMethodsInterceptPoint.getMethodsMatcher()))
                            .intercept(
                                MethodDelegation.withDefaultConfiguration()
                                    .to(new InstMethodsInter(interceptor, classLoader))
                            );
                }
            }
        }

        return newClassBuilder;
    }

    /**
     * Constructor methods intercept point. See {@link ConstructorInterceptPoint}
     *
     * @return collections of {@link ConstructorInterceptPoint}
     */
    protected abstract ConstructorInterceptPoint[] getConstructorsInterceptPoints();

    /**
     * Instance methods intercept point. See {@link InstanceMethodsInterceptPoint}
     *
     * @return collections of {@link InstanceMethodsInterceptPoint}
     */
    protected abstract InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints();

    /**
     * Enhance a class to intercept class static methods.
     * 增强类对静态方法的拦截
     *
     * @param enhanceOriginClassName target class name
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    private DynamicType.Builder<?> enhanceClass(String enhanceOriginClassName,
        DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader) throws PluginException {
        StaticMethodsInterceptPoint[] staticMethodsInterceptPoints = getStaticMethodsInterceptPoints();

        if (staticMethodsInterceptPoints == null || staticMethodsInterceptPoints.length == 0) {
            return newClassBuilder;
        }

        for (StaticMethodsInterceptPoint staticMethodsInterceptPoint : staticMethodsInterceptPoints) {
            String interceptor = staticMethodsInterceptPoint.getMethodsInterceptor();
            if (StringUtil.isEmpty(interceptor)) {
                throw new EnhanceException("no StaticMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
            }

            if (staticMethodsInterceptPoint.isOverrideArgs()) {
                newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher()))
                    .intercept(
                        MethodDelegation.withDefaultConfiguration()
                            .withBinders(
                                Morph.Binder.install(OverrideCallable.class)
                            )
                            .to(new StaticMethodsInterWithOverrideArgs(interceptor))
                    );
            } else {
                newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher()))
                    .intercept(
                        MethodDelegation.withDefaultConfiguration()
                            .to(new StaticMethodsInter(interceptor))
                    );
            }

        }

        return newClassBuilder;
    }

    /**
     * Static methods intercept point. See {@link StaticMethodsInterceptPoint}
     *
     * @return collections of {@link StaticMethodsInterceptPoint}
     */
    protected abstract StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints();
}
