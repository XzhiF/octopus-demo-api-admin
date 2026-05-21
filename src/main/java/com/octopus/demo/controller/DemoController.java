package com.octopus.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DemoController {

    @GetMapping("/demo")
    public Map<String, String> demo() {
        return Map.of("message", "hello from octopus-demo-api-admin");
    }
}