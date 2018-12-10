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

import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * The <code>AbstractTracerContext</code> represents the tracer context manager.
 *
 * AbstractTracerContext表示跟踪器上下文管理器。真正的链路跟踪管理器,而ContextManager只是包装了AbstractTracerContext的功能从而提供给外部使用
 *
 * @author wusheng
 */
public interface AbstractTracerContext {
    /**
     * Prepare for the cross-process propagation.
     * How to initialize the carrier, depends on the implementation.
     *
     * @param carrier to carry the context for crossing process.
     */
    void inject(ContextCarrier carrier);

    /**
     * Build the reference between this segment and a cross-process segment.
     * 构建此段与跨流程段之间的引用。
     * How to build, depends on the implementation.
     *
     * @param carrier carried the context from a cross-process segment.
     */
    void extract(ContextCarrier carrier);

    /**
     * Capture a snapshot for cross-thread propagation.
     * It's a similar concept with ActiveSpan.Continuation in OpenTracing-java
     * How to build, depends on the implementation.
     * 捕获快照以进行跨线程传播。 这与OpenSpracing-java中的ActiveSpan.Continuation类似。如何构建，取决于实现。
     *
     * @return the {@link ContextSnapshot} , which includes the reference context.
     */
    ContextSnapshot capture();

    /**
     * Build the reference between this segment and a cross-thread segment.
     * How to build, depends on the implementation.
     *
     * 构建此段与跨线程段之间的引用。 如何构建，取决于实现。
     *
     * @param snapshot from {@link #capture()} in the parent thread.
     */
    void continued(ContextSnapshot snapshot);

    /**
     * Get the global trace id, if needEnhance.
     * How to build, depends on the implementation.
     * 获取全局跟踪ID，如果需要增强。
     *       *如何构建，取决于实现。
     *
     * @return the string represents the id.
     */
    String getReadableGlobalTraceId();

    /**
     * Create an entry span
     *
     * 创建一个EntrySpan
     *
     * @param operationName most likely a service name  很可能是一个服务的名称。
     * @return the span represents an entry point of this segment. 表示该段的入口点。
     */
    AbstractSpan createEntrySpan(String operationName);

    /**
     * Create a local span
     *
     * 创建一个LocalSpan
     *
     * @param operationName most likely a local method signature, or business name.  很可能是本地方法签名或商业名称。
     * @return the span represents a local logic block.
     */
    AbstractSpan createLocalSpan(String operationName);

    /**
     * Create an exit span
     * 创建一个ExitSpan
     * @param operationName most likely a service name of remote 很可能是远程服务名称
     * @param remotePeer the network id(ip:port, hostname:port or ip1:port1,ip2,port, etc.)
     * @return the span represent an exit point of this segment.
     */
    AbstractSpan createExitSpan(String operationName, String remotePeer);

    /**
     * @return the active span of current tracing context(stack) 当前跟踪上下文（堆栈）的活动跨度
     */
    AbstractSpan activeSpan();

    /**
     * Finish the given span, and the given span should be the active span of current tracing context(stack)
     * 完成给定的span，给定的span应该是当前跟踪上下文（堆栈）的有效span
     *
     * @param span to finish
     */
    void stopSpan(AbstractSpan span);

    /**
     * @return the runtime context from current tracing context.
     * 返回当前链路跟踪的运行时上下文
     */
    RuntimeContext getRuntimeContext();
}
