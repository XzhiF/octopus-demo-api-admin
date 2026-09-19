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
    void truncateTruncatesOverLongText() throws Exception {
        mockMvc.perform(get("/demo/truncate").param("text", "abcdefghij").param("len", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("abcd…"));
    }

    @Test
    void truncateKeepsShortTextAsIs() throws Exception {
        mockMvc.perform(get("/demo/truncate").param("text", "ab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ab"));
    }
}
