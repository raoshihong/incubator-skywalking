package com.rao.study.apm.skywalking.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @PostMapping(value = "/login")
    public void login(){
        System.out.println("sdf");
    }

}
