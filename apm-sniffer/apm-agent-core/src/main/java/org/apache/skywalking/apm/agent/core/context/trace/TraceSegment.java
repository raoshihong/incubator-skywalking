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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceIds;
import org.apache.skywalking.apm.agent.core.context.ids.GlobalIdGenerator;
import org.apache.skywalking.apm.agent.core.context.ids.ID;
import org.apache.skywalking.apm.agent.core.context.ids.NewDistributedTraceId;
import org.apache.skywalking.apm.network.language.agent.TraceSegmentObject;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;

/**
 * {@link TraceSegment} is a segment or fragment of the distributed trace. See https://github.com/opentracing/specification/blob/master/specification.md#the-opentracing-data-model
 * A {@link TraceSegment} means the segment, which exists in current {@link Thread}. And the distributed trace is formed
 * by multi {@link TraceSegment}s, because the distributed trace crosses multi-processes, multi-threads. <p>
 *  {@link TraceSegment}是分布式跟踪的片段或分段。
 *  {@link TraceSegment}表示当前{@link Thread}中存在的段。 分布式跟踪由多个{@link TraceSegment}组成，因为分布式跟踪跨越多进程，多线程。
 * @author wusheng
 */
public class TraceSegment {
    /**
     * The id of this trace segment. Every segment has its unique-global-id.
     * 每个链路跟踪端的唯一id。 每个分段都有一个全局唯一的id
     */
    private ID traceSegmentId;

    /**
     * The refs of parent trace segments, except the primary one. For most RPC call, {@link #refs} contains only one
     * element, but if this segment is a start span of batch process, the segment faces multi parents, at this moment,
     * we use this {@link #refs} to link them.
     *
     * 父级跟踪段的引用，除主要跟踪段之外。 对于大多数RPC调用，{@link #refs}只包含一个元素，但如果此段是批处理的起始跨度，则该段面向多个父项，
     * 此时，我们使用此{@link #refs}链接它们。
     *
     * This field will not be serialized. Keeping this field is only for quick accessing.
     * 该字段不会被序列化。 保留此字段仅用于快速访问。
     */
    private List<TraceSegmentRef> refs;

    /**
     * The spans belong to this trace segment. They all have finished. All active spans are hold and controlled by
     * "skywalking-api" module.
     *
     * 跨度属于此跟踪段。 他们都已经完成了。 所有活动的跨度都由“skywalking-api”模块保持和控制。
     */
    private List<AbstractTracingSpan> spans;

    /**
     * The <code>relatedGlobalTraces</code> represent a set of all related trace. Most time it contains only one
     * element, because only one parent {@link TraceSegment} exists, but, in batch scenario, the num becomes greater
     * than 1, also meaning multi-parents {@link TraceSegment}. <p> The difference between
     * <code>relatedGlobalTraces</code> and {@link #refs} is: {@link #refs} targets this {@link TraceSegment}'s direct
     * parent, <p> and <p> <code>relatedGlobalTraces</code> targets this {@link TraceSegment}'s related call chain, a
     * call chain contains multi {@link TraceSegment}s, only using {@link #refs} is not enough for analysis and ui.
     */
    private DistributedTraceIds relatedGlobalTraces;

    private boolean ignore = false;

    private boolean isSizeLimited = false;

    /**
     * Create a default/empty trace segment, with current time as start time, and generate a new segment id.
     */
    public TraceSegment() {
        this.traceSegmentId = GlobalIdGenerator.generate();
        this.spans = new LinkedList<AbstractTracingSpan>();
        this.relatedGlobalTraces = new DistributedTraceIds();
        this.relatedGlobalTraces.append(new NewDistributedTraceId());
    }

    /**
     * Establish the link between this segment and its parents.
     *
     * @param refSegment {@link TraceSegmentRef}
     */
    public void ref(TraceSegmentRef refSegment) {
        if (refs == null) {
            refs = new LinkedList<TraceSegmentRef>();
        }
        if (!refs.contains(refSegment)) {
            refs.add(refSegment);
        }
    }

    /**
     * Establish the line between this segment and all relative global trace ids.
     */
    public void relatedGlobalTraces(DistributedTraceId distributedTraceId) {
        relatedGlobalTraces.append(distributedTraceId);
    }

    /**
     * After {@link AbstractSpan} is finished, as be controller by "skywalking-api" module, notify the {@link
     * TraceSegment} to archive it.
     *
     * @param finishedSpan
     */
    public void archive(AbstractTracingSpan finishedSpan) {
        spans.add(finishedSpan);
    }

    /**
     * Finish this {@link TraceSegment}. <p> return this, for chaining
     */
    public TraceSegment finish(boolean isSizeLimited) {
        this.isSizeLimited = isSizeLimited;
        return this;
    }

    public ID getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getApplicationId() {
        return RemoteDownstreamConfig.Agent.APPLICATION_ID;
    }

    public boolean hasRef() {
        return !(refs == null || refs.size() == 0);
    }

    public List<TraceSegmentRef> getRefs() {
        return refs;
    }

    public List<DistributedTraceId> getRelatedGlobalTraces() {
        return relatedGlobalTraces.getRelatedGlobalTraces();
    }

    public boolean isSingleSpanSegment() {
        return this.spans != null && this.spans.size() == 1;
    }

    public boolean isIgnore() {
        return ignore;
    }

    public void setIgnore(boolean ignore) {
        this.ignore = ignore;
    }

    /**
     * This is a high CPU cost method, only called when sending to collector or test cases.
     *
     * @return the segment as GRPC service parameter
     */
    public UpstreamSegment transform() {
        UpstreamSegment.Builder upstreamBuilder = UpstreamSegment.newBuilder();
        for (DistributedTraceId distributedTraceId : getRelatedGlobalTraces()) {
            upstreamBuilder = upstreamBuilder.addGlobalTraceIds(distributedTraceId.toUniqueId());
        }
        TraceSegmentObject.Builder traceSegmentBuilder = TraceSegmentObject.newBuilder();
        /**
         * Trace Segment
         */
        traceSegmentBuilder.setTraceSegmentId(this.traceSegmentId.transform());
        // Don't serialize TraceSegmentReference

        // SpanObject
        for (AbstractTracingSpan span : this.spans) {
            traceSegmentBuilder.addSpans(span.transform());
        }
        traceSegmentBuilder.setApplicationId(RemoteDownstreamConfig.Agent.APPLICATION_ID);
        traceSegmentBuilder.setApplicationInstanceId(RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID);
        traceSegmentBuilder.setIsSizeLimited(this.isSizeLimited);

        upstreamBuilder.setSegment(traceSegmentBuilder.build().toByteString());
        return upstreamBuilder.build();
    }

    @Override
    public String toString() {
        return "TraceSegment{" +
            "traceSegmentId='" + traceSegmentId + '\'' +
            ", refs=" + refs +
            ", spans=" + spans +
            ", relatedGlobalTraces=" + relatedGlobalTraces +
            '}';
    }

    public int getApplicationInstanceId() {
        return RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID;
    }
}
