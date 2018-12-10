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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.EntrySpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LocalSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.context.trace.WithPeerInfo;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryManager;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.dictionary.PossibleFound;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;

/**
 * The <code>TracingContext</code> represents a core tracing logic controller. It build the final {@link
 * TracingContext}, by the stack mechanism, which is similar with the codes work.
 *
 * TracingContext表示核心跟踪逻辑控制器。 它通过堆栈机制构建最终的{@link TracingContext}，这与代码工作类似。
 *
 * In opentracing concept, it means, all spans in a segment tracing context(thread) are CHILD_OF relationship, but no
 * FOLLOW_OF.
 *
 * 在opentracing概念中，它意味着，段跟踪上下文（线程）中的所有span都是CHILD_OF关系，但没有FOLLOW_OF。
 *
 * In skywalking core concept, FOLLOW_OF is an abstract concept when cross-process MQ or cross-thread async/batch tasks
 * happen, we used {@link TraceSegmentRef} for these scenarios. Check {@link TraceSegmentRef} which is from {@link
 * ContextCarrier} or {@link ContextSnapshot}.
 *
 *
 * 核心链路跟踪上下文管理实现类
 *
 * @author wusheng
 * @author zhang xin
 */
public class TracingContext implements AbstractTracerContext {
    private static final ILog logger = LogManager.getLogger(TracingContext.class);
    private long lastWarningTimestamp = 0;

    /**
     * @see {@link SamplingService}
     */
    private SamplingService samplingService;

    /**
     * The final {@link TraceSegment}, which includes all finished spans.
     */
    private TraceSegment segment;

    /**
     * Active spans stored in a Stack, usually called 'ActiveSpanStack'. This {@link LinkedList} is the in-memory
     * storage-structure. <p> I use {@link LinkedList#removeLast()}, {@link LinkedList#addLast(Object)} and {@link
     * LinkedList#last} instead of {@link #pop()}, {@link #push(AbstractSpan)}, {@link #peek()}
     */
    private LinkedList<AbstractSpan> activeSpanStack = new LinkedList<AbstractSpan>();

    /**
     * A counter for the next span.
     */
    private int spanIdGenerator;

    /**
     * Runtime context of the tracing context
     */
    private RuntimeContext runtimeContext;

    /**
     * Initialize all fields with default value.
     */
    TracingContext() {
        this.segment = new TraceSegment();
        this.spanIdGenerator = 0;
        if (samplingService == null) {
            samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
        }
    }

    /**
     * Inject the context into the given carrier, only when the active span is an exit one.
     *
     * 仅当活动的span为退出时，才将上下文注入到给定的carrier载体中。
     *
     * @param carrier to carry the context for crossing process.
     * @throws IllegalStateException if the active span isn't an exit one.
     * Ref to {@link AbstractTracerContext#inject(ContextCarrier)}
     */
    @Override
    public void inject(ContextCarrier carrier) {
        //获取当前线程中链路活跃的span
        AbstractSpan span = this.activeSpan();
        if (!span.isExit()) {
            //如果span不是在退出时,则不能注入
            throw new IllegalStateException("Inject can be done only in Exit Span");
        }

        //查看ExitSpan ,ExitSpan实现了WithPeerInfo
        WithPeerInfo spanWithPeer = (WithPeerInfo)span;
        String peer = spanWithPeer.getPeer();
        int peerId = spanWithPeer.getPeerId();

        //将分布式链路分段相关信息保存到carrier载荷中
        //保存分段链路id
        carrier.setTraceSegmentId(this.segment.getTraceSegmentId());
        //spanid
        carrier.setSpanId(span.getSpanId());

        //上级应用实例id
        carrier.setParentApplicationInstanceId(segment.getApplicationInstanceId());

        if (DictionaryUtil.isNull(peerId)) {
            carrier.setPeerHost(peer);
        } else {
            carrier.setPeerId(peerId);
        }

        //getRefs获取父级调用链的分段引用
        List<TraceSegmentRef> refs = this.segment.getRefs();
        int operationId;
        String operationName;
        int entryApplicationInstanceId;
        if (refs != null && refs.size() > 0) {
            TraceSegmentRef ref = refs.get(0);
            operationId = ref.getEntryOperationId();
            operationName = ref.getEntryOperationName();
            entryApplicationInstanceId = ref.getEntryApplicationInstanceId();
        } else {
            AbstractSpan firstSpan = first();
            operationId = firstSpan.getOperationId();
            operationName = firstSpan.getOperationName();
            entryApplicationInstanceId = this.segment.getApplicationInstanceId();
        }
        carrier.setEntryApplicationInstanceId(entryApplicationInstanceId);

        if (operationId == DictionaryUtil.nullValue()) {
            carrier.setEntryOperationName(operationName);
        } else {
            carrier.setEntryOperationId(operationId);
        }

        int parentOperationId = first().getOperationId();
        if (parentOperationId == DictionaryUtil.nullValue()) {
            carrier.setParentOperationName(first().getOperationName());
        } else {
            carrier.setParentOperationId(parentOperationId);
        }

        carrier.setDistributedTraceIds(this.segment.getRelatedGlobalTraces());
    }

    /**
     * Extract the carrier to build the reference for the pre segment.
     *
     * @param carrier carried the context from a cross-process segment.
     * Ref to {@link AbstractTracerContext#extract(ContextCarrier)}
     */
    @Override
    public void extract(ContextCarrier carrier) {
        //获取TraceSegmentRef引用,引用上一个链路中的TraceSegment
        TraceSegmentRef ref = new TraceSegmentRef(carrier);
        this.segment.ref(ref);
        //设置分段的全局id
        this.segment.relatedGlobalTraces(carrier.getDistributedTraceId());
        //获取最后一个有效的span
        AbstractSpan span = this.activeSpan();
        if (span instanceof EntrySpan) {
            span.ref(ref);
        }
    }

    /**
     * Capture the snapshot of current context.
     *
     * @return the snapshot of context for cross-thread propagation
     * Ref to {@link AbstractTracerContext#capture()}
     */
    @Override
    public ContextSnapshot capture() {
        List<TraceSegmentRef> refs = this.segment.getRefs();
        ContextSnapshot snapshot = new ContextSnapshot(segment.getTraceSegmentId(),
            activeSpan().getSpanId(),
            segment.getRelatedGlobalTraces());
        int entryOperationId;
        String entryOperationName;
        int entryApplicationInstanceId;
        AbstractSpan firstSpan = first();
        if (refs != null && refs.size() > 0) {
            TraceSegmentRef ref = refs.get(0);
            entryOperationId = ref.getEntryOperationId();
            entryOperationName = ref.getEntryOperationName();
            entryApplicationInstanceId = ref.getEntryApplicationInstanceId();
        } else {
            entryOperationId = firstSpan.getOperationId();
            entryOperationName = firstSpan.getOperationName();
            entryApplicationInstanceId = this.segment.getApplicationInstanceId();
        }
        snapshot.setEntryApplicationInstanceId(entryApplicationInstanceId);

        if (entryOperationId == DictionaryUtil.nullValue()) {
            snapshot.setEntryOperationName(entryOperationName);
        } else {
            snapshot.setEntryOperationId(entryOperationId);
        }

        if (firstSpan.getOperationId() == DictionaryUtil.nullValue()) {
            snapshot.setParentOperationName(firstSpan.getOperationName());
        } else {
            snapshot.setParentOperationId(firstSpan.getOperationId());
        }
        return snapshot;
    }

    /**
     * Continue the context from the given snapshot of parent thread.
     *
     * @param snapshot from {@link #capture()} in the parent thread.
     * Ref to {@link AbstractTracerContext#continued(ContextSnapshot)}
     */
    @Override
    public void continued(ContextSnapshot snapshot) {
        TraceSegmentRef segmentRef = new TraceSegmentRef(snapshot);
        this.segment.ref(segmentRef);
        this.activeSpan().ref(segmentRef);
        this.segment.relatedGlobalTraces(snapshot.getDistributedTraceId());
    }

    /**
     * @return the first global trace id.
     */
    @Override
    public String getReadableGlobalTraceId() {
        return segment.getRelatedGlobalTraces().get(0).toString();
    }

    /**
     * Create an entry span
     * 创建一个EntrySpan
     * @param operationName most likely a service name
     * @return span instance.
     * Ref to {@link EntrySpan}
     */
    @Override
    public AbstractSpan createEntrySpan(final String operationName) {
        if (isLimitMechanismWorking()) {
            NoopSpan span = new NoopSpan();
            return push(span);
        }
        AbstractSpan entrySpan;
        final AbstractSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        if (parentSpan != null && parentSpan.isEntry()) {
            entrySpan = (AbstractTracingSpan)DictionaryManager.findOperationNameCodeSection()
                .findOnly(segment.getApplicationId(), operationName)
                .doInCondition(new PossibleFound.FoundAndObtain() {
                    @Override public Object doProcess(int operationId) {
                        return parentSpan.setOperationId(operationId);
                    }
                }, new PossibleFound.NotFoundAndObtain() {
                    @Override public Object doProcess() {
                        return parentSpan.setOperationName(operationName);
                    }
                });
            return entrySpan.start();
        } else {
            entrySpan = (AbstractTracingSpan)DictionaryManager.findOperationNameCodeSection()
                .findOnly(segment.getApplicationId(), operationName)
                .doInCondition(new PossibleFound.FoundAndObtain() {
                    @Override public Object doProcess(int operationId) {
                        return new EntrySpan(spanIdGenerator++, parentSpanId, operationId);
                    }
                }, new PossibleFound.NotFoundAndObtain() {
                    @Override public Object doProcess() {
                        return new EntrySpan(spanIdGenerator++, parentSpanId, operationName);
                    }
                });

            //启动,并记录启动时间
            entrySpan.start();
            return push(entrySpan);
        }
    }

    /**
     * Create a local span
     *
     * @param operationName most likely a local method signature, or business name.
     * @return the span represents a local logic block.
     * Ref to {@link LocalSpan}
     */
    @Override
    public AbstractSpan createLocalSpan(final String operationName) {
        if (isLimitMechanismWorking()) {
            NoopSpan span = new NoopSpan();
            return push(span);
        }
        AbstractSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        AbstractTracingSpan span = (AbstractTracingSpan)DictionaryManager.findOperationNameCodeSection()
            .findOrPrepare4Register(segment.getApplicationId(), operationName, false, false)
            .doInCondition(new PossibleFound.FoundAndObtain() {
                @Override
                public Object doProcess(int operationId) {
                    return new LocalSpan(spanIdGenerator++, parentSpanId, operationId);
                }
            }, new PossibleFound.NotFoundAndObtain() {
                @Override
                public Object doProcess() {
                    return new LocalSpan(spanIdGenerator++, parentSpanId, operationName);
                }
            });
        span.start();
        return push(span);
    }

    /**
     * Create an exit span
     *
     * @param operationName most likely a service name of remote
     * @param remotePeer the network id(ip:port, hostname:port or ip1:port1,ip2,port, etc.)
     * @return the span represent an exit point of this segment.
     * @see ExitSpan
     */
    @Override
    public AbstractSpan createExitSpan(final String operationName, final String remotePeer) {
        AbstractSpan exitSpan;
        //获取调用链中最后一个span
        AbstractSpan parentSpan = peek();
        if (parentSpan != null && parentSpan.isExit()) {
            //如果parentSpan为exit状态,则表示父级已经退出,则使用parentSpan
            exitSpan = parentSpan;
        } else {
            //获取当前链路节点的父节点spanId
            final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();

            exitSpan = (AbstractSpan)DictionaryManager.findNetworkAddressSection()
                .find(remotePeer).doInCondition(
                    new PossibleFound.FoundAndObtain() {
                        @Override
                        public Object doProcess(final int peerId) {
                            if (isLimitMechanismWorking()) {
                                return new NoopExitSpan(peerId);
                            }

                            return DictionaryManager.findOperationNameCodeSection()
                                .findOnly(segment.getApplicationId(), operationName)
                                .doInCondition(
                                    new PossibleFound.FoundAndObtain() {
                                        @Override
                                        public Object doProcess(int operationId) {
                                            //在这里构建exitSpan
                                            return new ExitSpan(spanIdGenerator++, parentSpanId, operationId, peerId);
                                        }
                                    }, new PossibleFound.NotFoundAndObtain() {
                                        @Override
                                        public Object doProcess() {
                                            return new ExitSpan(spanIdGenerator++, parentSpanId, operationName, peerId);
                                        }
                                    });
                        }
                    },
                    new PossibleFound.NotFoundAndObtain() {
                        @Override
                        public Object doProcess() {
                            if (isLimitMechanismWorking()) {
                                return new NoopExitSpan(remotePeer);
                            }

                            return DictionaryManager.findOperationNameCodeSection()
                                .findOnly(segment.getApplicationId(), operationName)
                                .doInCondition(
                                    new PossibleFound.FoundAndObtain() {
                                        @Override
                                        public Object doProcess(int operationId) {
                                            return new ExitSpan(spanIdGenerator++, parentSpanId, operationId, remotePeer);
                                        }
                                    }, new PossibleFound.NotFoundAndObtain() {
                                        @Override
                                        public Object doProcess() {
                                            return new ExitSpan(spanIdGenerator++, parentSpanId, operationName, remotePeer);
                                        }
                                    });
                        }
                    });

            //添加这个span
            push(exitSpan);
        }
        exitSpan.start();
        return exitSpan;
    }

    /**
     * @return the active span of current context, the top element of {@link #activeSpanStack}
     */
    @Override
    public AbstractSpan activeSpan() {
        AbstractSpan span = peek();
        if (span == null) {
            throw new IllegalStateException("No active span.");
        }
        return span;
    }

    /**
     * Stop the given span, if and only if this one is the top element of {@link #activeSpanStack}. Because the tracing
     * core must make sure the span must match in a stack module, like any program did.
     * 停止给定的跨度，当且仅当这个是{@link #activeSpanStack}的顶级元素时
     * 因为跟踪核心必须确保跨度必须在堆栈模块中匹配，就像任何程序一样。
     * @param span to finish
     */
    @Override
    public void stopSpan(AbstractSpan span) {
        AbstractSpan lastSpan = peek();
        if (lastSpan == span) {
            if (lastSpan instanceof AbstractTracingSpan) {
                AbstractTracingSpan toFinishSpan = (AbstractTracingSpan)lastSpan;
                if (toFinishSpan.finish(segment)) {
                    pop();
                }
            } else {
                //移除当前的span
                pop();
            }
        } else {
            throw new IllegalStateException("Stopping the unexpected span = " + span);
        }

        if (activeSpanStack.isEmpty()) {
            this.finish();
        }
    }

    @Override
    public RuntimeContext getRuntimeContext() {
        if (runtimeContext == null) {
            runtimeContext = new RuntimeContext();
        }
        return runtimeContext;
    }

    /**
     * Finish this context, and notify all {@link TracingContextListener}s, managed by {@link
     * TracingContext.ListenerManager}
     */
    private void finish() {
        TraceSegment finishedSegment = segment.finish(isLimitMechanismWorking());
        /**
         * Recheck the segment if the segment contains only one span.
         * Because in the runtime, can't sure this segment is part of distributed trace.
         *
         * @see {@link #createSpan(String, long, boolean)}
         */
        if (!segment.hasRef() && segment.isSingleSpanSegment()) {
            if (!samplingService.trySampling()) {
                finishedSegment.setIgnore(true);
            }
        }
        TracingContext.ListenerManager.notifyFinish(finishedSegment);
    }

    /**
     * The <code>ListenerManager</code> represents an event notify for every registered listener, which are notified
     * when the <code>TracingContext</code> finished, and {@link #segment} is ready for further process.
     */
    public static class ListenerManager {
        private static List<TracingContextListener> LISTENERS = new LinkedList<TracingContextListener>();

        /**
         * Add the given {@link TracingContextListener} to {@link #LISTENERS} list.
         *
         * @param listener the new listener.
         */
        public static synchronized void add(TracingContextListener listener) {
            LISTENERS.add(listener);
        }

        /**
         * Notify the {@link TracingContext.ListenerManager} about the given {@link TraceSegment} have finished. And
         * trigger {@link TracingContext.ListenerManager} to notify all {@link #LISTENERS} 's {@link
         * TracingContextListener#afterFinished(TraceSegment)}
         *
         * @param finishedSegment
         */
        static void notifyFinish(TraceSegment finishedSegment) {
            for (TracingContextListener listener : LISTENERS) {
                listener.afterFinished(finishedSegment);
            }
        }

        /**
         * Clear the given {@link TracingContextListener}
         */
        public static synchronized void remove(TracingContextListener listener) {
            LISTENERS.remove(listener);
        }

    }

    /**
     * @return the top element of 'ActiveSpanStack', and remove it.
     */
    private AbstractSpan pop() {
        return activeSpanStack.removeLast();
    }

    /**
     * Add a new Span at the top of 'ActiveSpanStack'
     *
     * @param span
     */
    private AbstractSpan push(AbstractSpan span) {
        activeSpanStack.addLast(span);
        return span;
    }

    /**
     * @return the top element of 'ActiveSpanStack' only.
     */
    private AbstractSpan peek() {
        if (activeSpanStack.isEmpty()) {
            return null;
        }
        //返回调用链的最后一个span
        return activeSpanStack.getLast();
    }

    private AbstractSpan first() {
        return activeSpanStack.getFirst();
    }

    private boolean isLimitMechanismWorking() {
        if (spanIdGenerator >= Config.Agent.SPAN_LIMIT_PER_SEGMENT) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastWarningTimestamp > 30 * 1000) {
                logger.warn(new RuntimeException("Shadow tracing context. Thread dump"), "More than {} spans required to create",
                    Config.Agent.SPAN_LIMIT_PER_SEGMENT);
                lastWarningTimestamp = currentTimeMillis;
            }
            return true;
        } else {
            return false;
        }
    }
}
