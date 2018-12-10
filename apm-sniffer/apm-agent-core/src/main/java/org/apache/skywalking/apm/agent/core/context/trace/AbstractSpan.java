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

package org.apache.skywalking.apm.agent.core.context.trace;

import java.util.Map;
import org.apache.skywalking.apm.network.trace.component.Component;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * The <code>AbstractSpan</code> represents the span's skeleton, which contains all open methods.
 *
 * AbstractSpan表示span的骨架，其中包含所有打开的方法。
 *
 * SPAN，全称为Switched Port Analyzer，直译为交换端口分析器。是一种交换机的端口镜像技术
 * 作用主要是为了给某种网络分析器提供网络数据流，SPAN并不会影响源端口的数据交换，它只是将源端口发送或接收的数据包副本发送到监控端口。
 *
 *
 * @author wusheng
 */
public interface AbstractSpan {
    /**
     * Set the component id, which defines in {@link ComponentsDefine}
     *  设置组件ID，在{@link ComponentsDefine}中定义
     * @param component
     * @return the span for chaining. 返回调用链的span对象
     */
    AbstractSpan setComponent(Component component);

    /**
     * Only use this method in explicit instrumentation, like opentracing-skywalking-bridge. It it higher recommend
     * don't use this for performance consideration.
     *
     * 仅在显式检测中使用此方法，例如opentracing-skywalking-bridge，建议不要将其用于性能考虑。
     *
     * @param componentName
     * @return the span for chaining.
     */
    AbstractSpan setComponent(String componentName);

    AbstractSpan setLayer(SpanLayer layer);

    /**
     * Set a key:value tag on the Span.
     *  设置标签的key:value
     * @return this Span instance, for chaining 返回调用链路的实例
     */
    AbstractSpan tag(String key, String value);

    /**
     * Record an exception event of the current walltime timestamp.
     * 记录当前Walltime时间戳的异常事件
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    AbstractSpan log(Throwable t);

    AbstractSpan errorOccurred();

    /**
     * @return true if the actual span is an entry span.
     * 返回true表示当前span为entrySpan
     */
    boolean isEntry();

    /**
     * @return true if the actual span is an exit span.
     * 返回true表示当前span为ExitSpan
     */
    boolean isExit();

    /**
     * Record an event at a specific timestamp.
     * 在特定时间戳记录事件。
     *
     * @param timestamp The explicit timestamp for the log record.
     * @param event the events
     * @return the Span, for chaining
     */
    AbstractSpan log(long timestamp, Map<String, ?> event);

    /**
     * Sets the string name for the logical operation this span represents.
     * 设置此span表示的逻辑运算的字符串名称。
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan setOperationName(String operationName);

    /**
     * Start a span.
     *  启动一个span,表示开始链路跟踪
     * @return this Span instance, for chaining
     */
    AbstractSpan start();

    /**
     * Get the id of span
     *  每个span的唯一id
     * @return id value.
     */
    int getSpanId();

    /**
     * 操作id
     * @return
     */
    int getOperationId();

    String getOperationName();

    AbstractSpan setOperationId(int operationId);

    /**
     * Reference other trace segment.
     * 引用其他链路跟踪
     * @param ref segment ref
     */
    void ref(TraceSegmentRef ref);

    AbstractSpan start(long starttime);
}
