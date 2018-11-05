> 项目分为

    sky-walking-collector       这个就是监测服务端，将客户端传递过来的数据保存到数据库或者ES中
    sky-walking-agent  这个就是监测客户端，通过探针技术连接目标jvm虚拟机，收集相关信息，反馈给监测服务端
    sky-walking-ui  这个就是监测运维界面，通过ui界面进行可视化线上监测服务端的数据，及统计



> 启动sky-walking-collector 监测服务端

    1.在项目根目录执行mvn compile -Dmaven.test.skip=true
        执行完后，会自动生成packages包以及使用maven插件<artifactId>protobuf-maven-plugin</artifactId>，根据proto文件生成java文件
        使用的是grpc协议，在以下两个目录下会生成相应的java源码文件
        /apm-network/target/generated-sources/protobuf/ 下的 grpc-java 和 java 目录
        /apm-collector-remote/collector-remote-grpc-provider/target/generated-sources/protobuf/ 下的 grpc-java 和 java 目录
    
    注意：如果是在根目录下添加自己的demo项目进行调试，则需要将项目pom中的checkstyle插件去掉，否则编译不过

    2.运行org.skywalking.apm.collector.boot.CollectorBootStartUp 启动 Collector 
    3.访问 http://127.0.0.1:10800/agent/jetty 地址，返回 ["localhost:12800/"] ，说明启动成功

> 启动sky-walking-agent 监测客户端

    1. 执行mvn compile -Dmaven.test.skip=true 进行编译项目后,会生成一个packages目录。在 /packages/skywalking-agent 目录会生成sky-walking-agent的相关文件jar

    2.在跟目录下创建自己的demo项目，我这里使用springboot建的是一个web项目

    3.在apm-agent下 org.skywalking.apm.agent.SkyWalkingAgent的premain方法上打个断点

    4.运行自己的demo的Applicatin.main方法前添加jvm运行时参数,指明-javaagent探针代理包路径-javaagent:D:\incubator-skywalking\packages\skywalking-agent\skywalking-agent.jar

    5.如果在【第三步】的调试断点停住，说明 Agent 启动成功。同时也可以在Collector的控制台看到有agentUUID的输出，表示agent启动成功了
    
> 启动sky-walking-ui 监测运维界面

    1.在sky-walking-agent v3.2.6版本中，使用的还是springboot spring mvc实现的web项目,没有使用前后端分离，
    在sky-walking-agent v5.x 中开始将ui分离出来,使用node进行部署，所以我们在部署v3.2.6时可以用同一个版本的ui
    https://github.com/raoshihong/incubator-skywalking-ui.git
    
    v3.2.6的界面有点丑,所以可以切换到v5的版本
    
    2.使用http://localhost:8080进行访问
    
    
    
    