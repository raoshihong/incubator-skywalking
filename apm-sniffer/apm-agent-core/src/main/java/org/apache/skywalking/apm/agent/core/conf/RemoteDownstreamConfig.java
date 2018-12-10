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


package org.apache.skywalking.apm.agent.core.conf;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;

/**
 * The <code>RemoteDownstreamConfig</code> includes configurations from collector side.
 * RemoteDownstreamConfig包括收集器端的配置。
 * All of them initialized null, Null-Value or empty collection.
 * 所有这些都初始化了null，Null-Value或空集合。
 *
 * 远程配置,这个类的配置信息可以查看CollectorDiscoveryService这个调度服务,这个服务会定时去发现apm-collector服务,并呼气配置信息
 *
 * @author wusheng
 */
public class RemoteDownstreamConfig {
    public static class Agent {//代理的配置
        public volatile static int APPLICATION_ID = DictionaryUtil.nullValue();

        //对应服务的id
        public volatile static int APPLICATION_INSTANCE_ID = DictionaryUtil.nullValue();
    }

    public static class Collector {//搜集器服务的配置,默认是通过grpc交互的
        /**
         * Collector GRPC-Service address.
         */
        public volatile static List<String> GRPC_SERVERS = new LinkedList<String>();
    }
}
