package com.octopus.demo.controller;

import com.octopus.demo.common.bean.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/demo")
    public R<String> demo() {
        return R.ok("hello from octopus-demo-api-admin");
    }
}
