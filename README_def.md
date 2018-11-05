>如何定制自己的apm-agent

    1.将项目重命名,编写自己的premain,并调用skywalking的org.skywalking.apm.agent.SkyWalkingAgent.premain方法
    2.在pom中指定Pre-Class 为自己的类，这样就可以制定自己的apm-agent了