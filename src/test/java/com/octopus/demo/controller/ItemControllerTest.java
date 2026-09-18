package com.octopus.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for the paginated item endpoint (ticket 02, spec KD2/KD3).
 * Verifies page slices, size effect, total, out-of-range empty page,
 * illegal-size clamp and the R wrapper structure — all served through
 * java-common's PageUtil (cross-repo reuse proof point).
 */
@WebMvcTest(ItemController.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // page=1 size=5 切片正确
    @Test
    void page1Size5_returnsFirstFiveInSeedOrder() throws Exception {
        mockMvc.perform(get("/api/items").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(5))
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.list[0].name").value("item-1"))
                .andExpect(jsonPath("$.data.list[4].id").value(5))
                .andExpect(jsonPath("$.data.list[4].name").value("item-5"));
    }

    // page=2 size=5 切片正确
    @Test
    void page2Size5_returnsSecondFive() throws Exception {
        mockMvc.perform(get("/api/items").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(6))
                .andExpect(jsonPath("$.data.list[0].name").value("item-6"))
                .andExpect(jsonPath("$.data.list[4].id").value(10))
                .andExpect(jsonPath("$.data.list[4].name").value("item-10"));
    }

    // size 生效:请求多大页就多大
    @Test
    void sizeIsHonored() throws Exception {
        mockMvc.perform(get("/api/items").param("page", "1").param("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(7));
    }

    // total=数据量,totalPage=ceil(25/10)=3
    @Test
    void totalReflectsSeedDataSize() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(25))
                .andExpect(jsonPath("$.data.totalPage").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(10));
    }

    // 越界页:200 且 list 空,total 如实
    @Test
    void pageBeyondEnd_returns200WithEmptyListButHonestTotal() throws Exception {
        mockMvc.perform(get("/api/items").param("page", "99").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(0))
                .andExpect(jsonPath("$.data.count").value(25))
                .andExpect(jsonPath("$.data.totalPage").value(5));
    }

    // 非法 size 被 clamp:size=0→20
    @Test
    void illegalSizeBelowOne_clampedTo20() throws Exception {
        mockMvc.perform(get("/api/items").param("page", "1").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(20))
                .andExpect(jsonPath("$.data.totalPage").value(2));
    }

    // 非法 size 被 clamp:size=500→100,数据只有 25 → 整份返回
    @Test
    void illegalSizeAboveMax_clampedTo100ReturnsAll() throws Exception {
        mockMvc.perform(get("/api/items").param("page", "1").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(25))
                .andExpect(jsonPath("$.data.count").value(25))
                .andExpect(jsonPath("$.data.totalPage").value(1));
    }

    // R 包装结构 {code,data,msg}(+timestamp)
    @Test
    void responseWrappedInR() throws Exception {
        mockMvc.perform(get("/api/items").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.timestamp").isNumber());
    }
}
