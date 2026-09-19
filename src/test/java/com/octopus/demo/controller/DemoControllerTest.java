package com.octopus.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoReturnsHelloMessage() throws Exception {
        mockMvc.perform(get("/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data").value("hello from octopus-demo-api-admin"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void luhnReturnsTrueForValidCardNumber() throws Exception {
        mockMvc.perform(get("/demo/luhn").param("no", "4539578763621486"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void luhnReturnsFalseForGarbageInput() throws Exception {
        mockMvc.perform(get("/demo/luhn").param("no", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void luhnReturnsFalseWhenParamMissing() throws Exception {
        mockMvc.perform(get("/demo/luhn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }
}
