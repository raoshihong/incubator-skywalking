package com.rao.study;

import com.rao.study.plugin.demo.TimeDemo;
import com.rao.study.plugin.demo.TraceDemo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.sql.Time;

@SpringBootApplication
public class SkyWalkingAgentTest {
    //主要与apm同级才能调试
    public static void main(String[] args) throws Exception{//执行时指定-javaagent:D:\incubator-skywalking\apm-sniffer\apm-agent\target\skywalking-agent.jar

//        test();
        SpringApplication.run(SkyWalkingAgentTest.class,args);
    }

    public static void test() throws Exception{
        System.out.println("sdsfs");

        TimeDemo timeDemo = new TimeDemo();
        timeDemo.fun1();
        timeDemo.fun2();

        TraceDemo traceDemo = new TraceDemo();
        traceDemo.funTrace("ssss","234124124213421342314");
    }
}
