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

package org.apache.skywalking.apm.agent.core.context;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * {@link ContextManager} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context. <p> What is 'ChildOf'?
 * https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans
 * {@link ContextManager}控制{@link TraceSegment}的整个上下文。 任何{@link TraceSegment}都与单线程有关，
 * 因此这个上下文使用{@link ThreadLocal}来维护上下文，并确保，因为{@link TraceSegment}启动，所有ChildOf跨度都在同一个上下文中。
 * 什么是'ChildOf'？可以查看https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans
 * <p> Also, {@link ContextManager} delegates to all {@link AbstractTracerContext}'s major methods.
 * 此外，{@link ContextManager}委托所有{@link AbstractTracerContext}的主要方法。
 * @author wusheng
 */
public class ContextManager implements TracingContextListener, BootService, IgnoreTracerContextListener {
    private static final ILog logger = LogManager.getLogger(ContextManager.class);
    private static ThreadLocal<AbstractTracerContext> CONTEXT = new ThreadLocal<AbstractTracerContext>();
    private static ContextManagerExtendService EXTEND_SERVICE;

    /**
     * 获取或创建TracerContext
     * @param operationName 操作者名称
     * @param forceSampling 是否强制采样
     * @return 返回的可能是IgnoredTracerContext 也可能是TracingContext
     */
    private static AbstractTracerContext getOrCreate(String operationName, boolean forceSampling) {
        //获取当前线程中的AbstractTracerContext,每个调用链路都是一个线程路线,所以一个线程路线中只会有一个链路上下文对象AbstractTracerContext
        AbstractTracerContext context = CONTEXT.get();
        if (EXTEND_SERVICE == null) {
            EXTEND_SERVICE = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);
        }
        if (context == null) {
            if (StringUtil.isEmpty(operationName)) {
                //操作者为空,则直接忽略这个trace,标记为ignored
                if (logger.isDebugEnable()) {
                    logger.debug("No operation name, ignore this trace.");
                }
                context = new IgnoredTracerContext();
            } else {
                //判断是否有collector服务器
                if (RemoteDownstreamConfig.Agent.APPLICATION_ID != DictionaryUtil.nullValue()
                    && RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID != DictionaryUtil.nullValue()
                    ) {

                    //有RemoteDownstreamConfig配置信息,则表示有apm-collector服务器,则可以正常进行分布式链路跟踪信息的收集和保存以及分析,所以这里创建一个TraceContext
                    context = EXTEND_SERVICE.createTraceContext(operationName, forceSampling);
                } else {
                    /**
                     * Can't register to collector, no need to trace anything.
                     * 无法注册到apm-collector的话，则无需记录任何追踪的内容。(意思就是apm-collector服务都不存在,就认为不需要对分布式链路信息进行保存和分析,所以可以认为可以忽略)
                     */
                    context = new IgnoredTracerContext();
                }
            }
            CONTEXT.set(context);
        }
        return context;
    }

    private static AbstractTracerContext get() {
        return CONTEXT.get();
    }

    /**
     * @return the first global trace id if needEnhance. Otherwise, "N/A".
     */
    public static String getGlobalTraceId() {
        AbstractTracerContext segment = CONTEXT.get();
        if (segment == null) {
            return "N/A";
        } else {
            return segment.getReadableGlobalTraceId();
        }
    }

    /**
     * 创建一个EntrySpan
     * @param operationName
     * @param carrier
     * @return
     */
    public static AbstractSpan createEntrySpan(String operationName, ContextCarrier carrier) {
        SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
        AbstractSpan span;
        AbstractTracerContext context;
        if (carrier != null && carrier.isValid()) {
            samplingService.forceSampled();
            context = getOrCreate(operationName, true);
            span = context.createEntrySpan(operationName);
            //构建当前链路与上一个链路的引用
            context.extract(carrier);
        } else {
            context = getOrCreate(operationName, false);
            span = context.createEntrySpan(operationName);
        }
        return span;
    }

    public static AbstractSpan createLocalSpan(String operationName) {
        AbstractTracerContext context = getOrCreate(operationName, false);
        return context.createLocalSpan(operationName);
    }

    /**
     * 创建一个ExitSpan 链路span
     * @param operationName  操作者名称
     * @param carrier  AbstractTraceContext数据承载者
     * @param remotePeer  远程窥探者
     * @return
     */
    public static AbstractSpan createExitSpan(String operationName, ContextCarrier carrier, String remotePeer) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        //获取或者创建一个TracerContext
        AbstractTracerContext context = getOrCreate(operationName, false);
        //通过链路跟踪上下文创建一个ExitSpan
        ////如果是IgnoredTraceContext,则只记录调用链的深度
        //如果是TraceContext
        AbstractSpan span = context.createExitSpan(operationName, remotePeer);
        //将carrier注入到context,表示carrier将会承载者context上下文中的信息
        //当为出口时,就将当前的carrier注入到上下文,以便下一个链路节点使用
        context.inject(carrier);
        return span;
    }

    public static AbstractSpan createExitSpan(String operationName, String remotePeer) {
        AbstractTracerContext context = getOrCreate(operationName, false);
        AbstractSpan span = context.createExitSpan(operationName, remotePeer);
        return span;
    }

    public static void inject(ContextCarrier carrier) {
        get().inject(carrier);
    }

    public static void extract(ContextCarrier carrier) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        if (carrier.isValid()) {
            get().extract(carrier);
        }
    }

    public static ContextSnapshot capture() {
        return get().capture();
    }

    public static void continued(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("ContextSnapshot can't be null.");
        }
        if (snapshot.isValid() && !snapshot.isFromCurrent()) {
            get().continued(snapshot);
        }
    }

    public static AbstractSpan activeSpan() {
        return get().activeSpan();
    }

    public static void stopSpan() {
        stopSpan(activeSpan());
    }

    public static void stopSpan(AbstractSpan span) {
        get().stopSpan(span);
    }

    @Override
    public void prepare() throws Throwable {

    }

    @Override
    public void boot() {
        ContextManagerExtendService service = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);
        service.registerListeners(this);
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override public void shutdown() throws Throwable {

    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        CONTEXT.remove();
    }

    @Override
    public void afterFinished(IgnoredTracerContext traceSegment) {
        CONTEXT.remove();
    }

    public static boolean isActive() {
        return get() != null;
    }

    public static RuntimeContext getRuntimeContext() {
        if (isActive()) {
            return get().getRuntimeContext();
        } else {
            throw new IllegalStateException("No active context");
        }
    }

}
