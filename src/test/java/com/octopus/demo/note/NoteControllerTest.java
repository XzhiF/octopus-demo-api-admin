package com.octopus.demo.note;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NoteController 三接口的 MockMvc 测试(票 02):201/400/200 列表/404 get/204/404 delete。
 * Store 为控制器内共享实例,用例间状态累积 —— 断言一律基于本用例自建的 id 与内容标记。
 */
@WebMvcTest(NoteController.class)
class NoteControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private long createNote(String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new NoteController.CreateNoteRequest(content))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").asLong();
    }

    @Test
    void postValidContentReturns201AndNoteInR() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"买咖啡豆\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.content").value("买咖啡豆"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void postBlankContentReturns400WithRFail() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void postEmptyContentReturns400() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void listReturns200WithNewestFirst() throws Exception {
        long olderId = createNote("列表-旧");
        createNote("列表-新");
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].content").value("列表-新"))
                .andExpect(jsonPath("$.data[?(@.id == " + olderId + ")].content").value("列表-旧"));
    }

    @Test
    void getKnownIdReturns200WithNote() throws Exception {
        long id = createNote("单查命中");
        mockMvc.perform(get("/api/notes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(Math.toIntExact(id)))
                .andExpect(jsonPath("$.data.content").value("单查命中"));
    }

    @Test
    void getUnknownIdReturns404WithRFail() throws Exception {
        mockMvc.perform(get("/api/notes/{id}", 987654))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void deleteKnownIdReturns204AndNoteGone() throws Exception {
        long id = createNote("待删除");
        mockMvc.perform(delete("/api/notes/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/notes/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUnknownIdReturns404WithRFail() throws Exception {
        mockMvc.perform(delete("/api/notes/{id}", 987654))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void postWithoutReadableBodyReturns400WithRFail() throws Exception {
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getNonNumericIdReturns400WithRFail() throws Exception {
        mockMvc.perform(get("/api/notes/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
