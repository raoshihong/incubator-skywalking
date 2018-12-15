package com.rao.study.apm.trace;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

public class MyTraceInterceptor implements InstanceMethodsAroundInterceptor {

    private long start = 0;

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        start = System.currentTimeMillis();
        String name = (String)allArguments[0];
        String token = (String)allArguments[1];
        //本地调用,创建一个本地的
        final ContextCarrier contextCarrier = new ContextCarrier();
        AbstractSpan span = ContextManager.createExitSpan(method.getName()+"args:{name="+name+",token="+token+"}", contextCarrier, "localhost:8911");
//        AbstractSpan span = ContextManager.createLocalSpan(method.getName()+"args:{name="+name+",token="+token+"}");
//        Tags.DB_TYPE.set(span, "sql");
//        Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
//        Tags.DB_STATEMENT.set(span, sql);
//        span.setComponent(connectInfo.getComponent());
//        SpanLayer.asDB(span);

        //设置span的tag
        Tags.URL.set(span, method.getName()+"args:{name="+name+",token="+token+"}");
        //设置当前调用的组件
        span.setComponent(ComponentsDefine.DUBBO);
        //设置span的布局layer为rpc的形式
        SpanLayer.asRPCFramework(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        System.out.println(method + ": took " + (System.currentTimeMillis() - start) + "ms");
        ContextManager.stopSpan();
        return null;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }
}
