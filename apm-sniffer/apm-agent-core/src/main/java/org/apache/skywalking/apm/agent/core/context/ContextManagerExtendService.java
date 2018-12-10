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
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;

/**
 * ContextManagerExtendService 上下文管理扩展service
 * @author wusheng
 */
@DefaultImplementor
public class ContextManagerExtendService implements BootService {
    @Override public void prepare() {

    }

    @Override public void boot() {

    }

    @Override public void onComplete() {

    }

    @Override public void shutdown() {

    }

    public void registerListeners(ContextManager manager) {
        TracingContext.ListenerManager.add(manager);
        IgnoredTracerContext.ListenerManager.add(manager);
    }

    /**
     * 创建一个TraceContext
     * @param operationName
     * @param forceSampling
     * @return
     */
    public AbstractTracerContext createTraceContext(String operationName, boolean forceSampling) {
        AbstractTracerContext context;
        int suffixIdx = operationName.lastIndexOf(".");
        if (suffixIdx > -1 && Config.Agent.IGNORE_SUFFIX.contains(operationName.substring(suffixIdx))) {
            context = new IgnoredTracerContext();
        } else {

            //查找到数据采集服务
            SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
            if (forceSampling || samplingService.trySampling()) {//表示可以尝试采集了
                //表示可以采集了,则创建一个TracingContext上下文
                context = new TracingContext();
            } else {
                //否则忽略这个
                context = new IgnoredTracerContext();
            }
        }

        return context;
    }
}
