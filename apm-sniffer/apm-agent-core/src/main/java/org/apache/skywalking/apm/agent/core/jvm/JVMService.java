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

package org.apache.skywalking.apm.agent.core.jvm;

import io.grpc.Channel;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.jvm.cpu.CPUProvider;
import org.apache.skywalking.apm.agent.core.jvm.gc.GCProvider;
import org.apache.skywalking.apm.agent.core.jvm.memory.MemoryProvider;
import org.apache.skywalking.apm.agent.core.jvm.memorypool.MemoryPoolProvider;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.network.language.agent.JVMMetric;
import org.apache.skywalking.apm.network.language.agent.JVMMetrics;
import org.apache.skywalking.apm.network.language.agent.JVMMetricsServiceGrpc;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The <code>JVMService</code> represents a timer,
 * which collectors JVM cpu, memory, memorypool and gc info,
 * and send the collected info to Collector through the channel provided by {@link GRPCChannelManager}
 *
 * JVMService代表一个计时器，它收集JVM cpu，内存，内存池和gc信息，并通过{@link GRPCChannelManager}提供的渠道将收集的信息发送给收集器。
 *
 * @author wusheng
 */
@DefaultImplementor
public class JVMService implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(JVMService.class);
    private LinkedBlockingQueue<JVMMetric> queue;
    //收集调度
    private volatile ScheduledFuture<?> collectMetricFuture;
    //发送调度
    private volatile ScheduledFuture<?> sendMetricFuture;
    private Sender sender;

    /**
     * 执行前的准备
     * @throws Throwable
     */
    @Override
    public void prepare() throws Throwable {
        //执行数据收集前的准备
        //初始化一个队列,默认队列大小为60*10
        queue = new LinkedBlockingQueue(Config.Jvm.BUFFER_SIZE);
        //创建一个发送器,这里默认为grpc
        sender = new Sender();
        //再通过GRPCChannelManager,对sender进行监听,监听到数据，则通过GRPCChannelManager将信息发送给apm-collector服务
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(sender);
    }

    /**
     * 服务执行方法
     * @throws Throwable
     */
    @Override
    public void boot() throws Throwable {
        //创建一个调度任务,任务名称为JVMService-produce
        //这个调度任务是执行this 对象的run，即当前JVMService自己的run方法,从0秒开始,每隔1秒执行一次
        collectMetricFuture = Executors
            .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("JVMService-produce"))
            .scheduleAtFixedRate(new RunnableWithExceptionProtection(this, new RunnableWithExceptionProtection.CallbackWhenException() {
                @Override public void handle(Throwable t) {
                    logger.error("JVMService produces metrics failure.", t);
                }
            }), 0, 1, TimeUnit.SECONDS);

        //发送任务,从0秒开始,每隔1秒执行一次,sender
        sendMetricFuture = Executors
            .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("JVMService-consume"))
            .scheduleAtFixedRate(new RunnableWithExceptionProtection(sender, new RunnableWithExceptionProtection.CallbackWhenException() {
                @Override public void handle(Throwable t) {
                    logger.error("JVMService consumes and upload failure.", t);
                }
            }
            ), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onComplete() throws Throwable {

    }

    /**
     * 关闭服务就是将这个service中的调度任务给取消
     * @throws Throwable
     */
    @Override
    public void shutdown() throws Throwable {
        collectMetricFuture.cancel(true);
        sendMetricFuture.cancel(true);
    }

    @Override
    public void run() {
        //搜集jvm的相关信息,如cpu,memory,gc等,将搜集到的信息放到queue队列中
        //先判断是否有被代理的应用,通过id和instance_id来判断
        if (RemoteDownstreamConfig.Agent.APPLICATION_ID != DictionaryUtil.nullValue()
            && RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID != DictionaryUtil.nullValue()
            ) {
            long currentTimeMillis = System.currentTimeMillis();
            try {
                //获取jvm信息
                JVMMetric.Builder jvmBuilder = JVMMetric.newBuilder();
                jvmBuilder.setTime(currentTimeMillis);
                jvmBuilder.setCpu(CPUProvider.INSTANCE.getCpuMetric());
                jvmBuilder.addAllMemory(MemoryProvider.INSTANCE.getMemoryMetricList());
                jvmBuilder.addAllMemoryPool(MemoryPoolProvider.INSTANCE.getMemoryPoolMetricList());
                jvmBuilder.addAllGc(GCProvider.INSTANCE.getGCList());

                JVMMetric jvmMetric = jvmBuilder.build();
                if (!queue.offer(jvmMetric)) {
                    queue.poll();
                    queue.offer(jvmMetric);
                }
            } catch (Exception e) {
                logger.error(e, "Collect JVM info fail.");
            }
        }
    }

    private class Sender implements Runnable, GRPCChannelListener {
        private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
        private volatile JVMMetricsServiceGrpc.JVMMetricsServiceBlockingStub stub = null;

        @Override
        public void run() {
            //执行发送,将搜集的jvm信息发送到apm-collector服务中
            if (RemoteDownstreamConfig.Agent.APPLICATION_ID != DictionaryUtil.nullValue()
                && RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID != DictionaryUtil.nullValue()
                ) {
                if (status == GRPCChannelStatus.CONNECTED) {
                    try {
                        JVMMetrics.Builder builder = JVMMetrics.newBuilder();
                        LinkedList<JVMMetric> buffer = new LinkedList<JVMMetric>();
                        queue.drainTo(buffer);
                        if (buffer.size() > 0) {
                            builder.addAllMetrics(buffer);
                            builder.setApplicationInstanceId(RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID);
                            //通过grpc发送信息
                            stub.collect(builder.build());
                        }
                    } catch (Throwable t) {
                        logger.error(t, "send JVM metrics to Collector fail.");
                    }
                }
            }
        }

        @Override
        public void statusChanged(GRPCChannelStatus status) {
            if (GRPCChannelStatus.CONNECTED.equals(status)) {
                Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
                stub = JVMMetricsServiceGrpc.newBlockingStub(channel);
            }
            this.status = status;
        }
    }
}
