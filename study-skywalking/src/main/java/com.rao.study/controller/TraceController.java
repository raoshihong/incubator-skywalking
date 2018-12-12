package com.rao.study.controller;

import com.rao.study.plugin.demo.TimeDemo;
import com.rao.study.plugin.demo.TraceDemo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TraceController {

    @GetMapping(value = {"/testTrace"})
    public void testTrace() throws Exception{
        System.out.println("sdsfs");

        TimeDemo timeDemo = new TimeDemo();
        timeDemo.fun1();
        timeDemo.fun2();

        TraceDemo traceDemo = new TraceDemo();
        traceDemo.funTrace("ssss","234124124213421342314");
    }

}
