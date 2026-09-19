package com.octopus.demo.controller;

import com.octopus.demo.common.bean.R;
import com.octopus.demo.common.util.TruncateUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/demo")
    public R<String> demo() {
        return R.ok("hello from octopus-demo-api-admin");
    }

    @GetMapping("/demo/truncate")
    public R<String> truncate(@RequestParam String text,
                              @RequestParam(defaultValue = "10") int len) {
        return R.ok(TruncateUtils.truncate(text, len));
    }
}
