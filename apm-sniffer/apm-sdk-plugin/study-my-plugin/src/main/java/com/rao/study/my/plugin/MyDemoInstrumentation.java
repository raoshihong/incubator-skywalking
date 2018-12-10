package com.rao.study.my.plugin;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class MyDemoInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    //表示要对这个目标类进行代理
    private static final String ENHANCE_CLASS = "com.rao.study.plugin.demo.TimeDemo";

    /**
     * 指明要代理目标程序的目标类
     * @return
     */
    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    /**
     * 构造方法拦截器的定义
     * @return
     */
    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    /**
     * 实例方法拦截器的定义,返回的是一个数组,表示可以指定对多个方法的拦截,也可以直接指定对目标类的任意方法的拦截
     * @return
     */
    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
                new InstanceMethodsInterceptPoint() {
                    //定义匹配(拦截)规则，即要拦截目标类的哪个方法
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("fun1");//表示对目标程序的TimeDemo类下的fun1方法进行拦截
                    }

                    //定义拦截器
                    @Override
                    public String getMethodsInterceptor() {
                        return "com.rao.study.my.plugin.MyDemoInterceptor";
                    }

                    //指明是否要覆盖参数
                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    //定义匹配(拦截)规则，即要拦截目标类的哪个方法
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("fun2");//表示对目标程序的TimeDemo类下的fun1方法进行拦截
                    }

                    //定义拦截器
                    @Override
                    public String getMethodsInterceptor() {
                        return "com.rao.study.my.plugin.MyDemoInterceptor";
                    }

                    //指明是否要覆盖参数
                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }

    /**
     * 指明见证列表类,用来区分版本的不同
     * @return
     */
    @Override
    protected String[] witnessClasses() {
        return super.witnessClasses();
    }
}
