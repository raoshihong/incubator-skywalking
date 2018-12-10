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

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link DubboInterceptor} define how to enhance class {@link com.alibaba.dubbo.monitor.support.MonitorFilter#invoke(Invoker,
 * Invocation)}. the trace context transport to the provider side by {@link RpcContext#attachments}.but all the version
 * of dubbo framework below 2.8.3 don't support {@link RpcContext#attachments}, we support another way to support it.
 *
 * {@link DubboInterceptor}定义了如何增强类{@link com.alibaba.dubbo.monitor.support.MonitorFilter #invoke（Invoker，Invocation）}。
 * 跟踪上下文通过{@link RpcContext＃attachments}传输到提供者端。
 * 但是2.8.3以下的dubbo框架的所有版本都不支持{@link RpcContext #attachments}，我们支持另一种方式来支持它。
 *
 * 在com.alibaba.dubbo.monitor.support.MonitorFilter的invoke方法调用时被拦截
 *
 * @author zhangxin
 */
public class DubboInterceptor implements InstanceMethodsAroundInterceptor {
    /**
     * <h2>Consumer:</h2> The serialized trace context data will
     * inject to the {@link RpcContext#attachments} for transport to provider side.
     * <p>
     * <h2>Provider:</h2> The serialized trace context data will extract from
     * {@link RpcContext#attachments}. current trace segment will ref if the serialize context data is not null.
     *
     * 消费者：序列化的跟踪上下文数据将注入{@link RpcContext＃attachments}以传输到提供者端。
     * 提供者：序列化的跟踪上下文数据将从{@link RpcContext #attachments}中提取。 如果序列化上下文数据不为null，则当前跟踪段将引用。
     *
     * beforeMethod方法在目标程序的目标类的目标方法调用前执行
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        //public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException
        //可以到dubbo框架中查看MonitorFilter.invoke方法的定义,可以看到allArguments[0] 就是一个Invoker类型

        Invoker invoker = (Invoker)allArguments[0];
        Invocation invocation = (Invocation)allArguments[1];
        //RpcContext是dubbo框架提供的,通过RpcContext
        RpcContext rpcContext = RpcContext.getContext();
        boolean isConsumer = rpcContext.isConsumerSide();
        URL requestURL = invoker.getUrl();

        //使用了分布式链路span
        AbstractSpan span;

        //获取dubbo调用的host
        final String host = requestURL.getHost();
        //获取dubbo调用的port
        final int port = requestURL.getPort();

        if (isConsumer) {//是订阅者

            //ContextCarrier用于承载AbstractTracerContext的链路跟踪信息
            final ContextCarrier contextCarrier = new ContextCarrier();

            //消费者端/接收端,所以创建的是一个ExitSpan
            //generateOperationName 记录操作者名称
            //作为本服务最终出口,则创建一个ExitSpan
            span = ContextManager.createExitSpan(generateOperationName(requestURL, invocation), contextCarrier, host + ":" + port);
            //invocation.getAttachments().put("contextData", contextDataStr);
            //@see https://github.com/alibaba/dubbo/blob/dubbo-2.5.3/dubbo-rpc/dubbo-rpc-api/src/main/java/com/alibaba/dubbo/rpc/RpcInvocation.java#L154-L161
            //这部分是实现attachments的
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                rpcContext.getAttachments().put(next.getHeadKey(), next.getHeadValue());
            }
        } else {
            //生产者
            ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            //处理attachment
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(rpcContext.getAttachment(next.getHeadKey()));
            }

            //主动发起的,则创建一个EntrySpan
            //创建一个EntrySpan
            span = ContextManager.createEntrySpan(generateOperationName(requestURL, invocation), contextCarrier);
        }


        //设置span的tag
        Tags.URL.set(span, generateRequestURL(requestURL, invocation));
        //设置当前调用的组件
        span.setComponent(ComponentsDefine.DUBBO);
        //设置span的布局layer为rpc的形式
        SpanLayer.asRPCFramework(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Result result = (Result)ret;
        if (result != null && result.getException() != null) {
            //如果异常不为空,则只需异常处理,在span中记录异常日志
            dealException(result.getException());
        }

        //整个链路节点调用完成了,name可以停止当前的span的记录了,实际就是从记录中移除当前的span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        dealException(t);
    }

    /**
     * Log the throwable, which occurs in Dubbo RPC service.
     */
    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(throwable);
    }

    /**
     * Format operation name. e.g. org.apache.skywalking.apm.plugin.test.Test.test(String)
     *
     * @return operation name.
     */
    private String generateOperationName(URL requestURL, Invocation invocation) {
        StringBuilder operationName = new StringBuilder();
        operationName.append(requestURL.getPath());
        operationName.append("." + invocation.getMethodName() + "(");
        for (Class<?> classes : invocation.getParameterTypes()) {
            operationName.append(classes.getSimpleName() + ",");
        }

        if (invocation.getParameterTypes().length > 0) {
            operationName.delete(operationName.length() - 1, operationName.length());
        }

        operationName.append(")");

        return operationName.toString();
    }

    /**
     * Format request url.
     * e.g. dubbo://127.0.0.1:20880/org.apache.skywalking.apm.plugin.test.Test.test(String).
     *
     * @return request url.
     */
    private String generateRequestURL(URL url, Invocation invocation) {
        StringBuilder requestURL = new StringBuilder();
        requestURL.append(url.getProtocol() + "://");
        requestURL.append(url.getHost());
        requestURL.append(":" + url.getPort() + "/");
        requestURL.append(generateOperationName(url, invocation));
        return requestURL.toString();
    }
}
