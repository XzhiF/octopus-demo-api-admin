package com.octopus.demo.controller;

import com.octopus.demo.common.bean.R;
import com.octopus.demo.common.util.IdCheckUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/demo")
    public R<String> demo() {
        return R.ok("hello from octopus-demo-api-admin");
    }

    @GetMapping("/demo/luhn")
    public R<Boolean> luhn(@RequestParam(name = "no", defaultValue = "") String no) {
        return R.ok(IdCheckUtils.luhn(no));
    }
}
