package com.rao.study;

import com.rao.study.plugin.demo.TimeDemo;

import java.sql.Time;

public class SkyWalkingAgentTest {
    //主要与apm同级才能调试
    public static void main(String[] args) throws Exception{//执行时指定-javaagent:D:\incubator-skywalking\apm-sniffer\apm-agent\target\skywalking-agent.jar
        System.out.println("sdsfs");

        TimeDemo timeDemo = new TimeDemo();
        timeDemo.fun1();
        timeDemo.fun2();
    }
}
