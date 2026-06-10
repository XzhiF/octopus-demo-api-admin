package com.octopus.demo.controller;

import com.octopus.demo.common.auth.AuthAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditSummaryController.class)
@Import(AuthAutoConfiguration.class)
class AuditSummaryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RestTemplate restTemplate;

    @Test
    @DisplayName("GET /api/gateway/audit-summary aggregates from both services when authenticated")
    void getAuditSummary_aggregatesBothServices() throws Exception {
        Map<String, Object> userResponse = Map.of(
            "code", 200,
            "data", List.of(Map.of(
                "timestamp", "2026-01-01T00:00:00Z",
                "userId", 1,
                "action", "CREATE",
                "entityType", "USER",
                "entityId", "1",
                "details", Map.of()
            ))
        );
        Map<String, Object> adminResponse = Map.of(
            "code", 200,
            "data", List.of()
        );

        when(restTemplate.exchange(contains("/api/users/audit"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(userResponse, HttpStatus.OK));
        when(restTemplate.exchange(contains("/api/admin/audit-logs"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(adminResponse, HttpStatus.OK));

        mockMvc.perform(get("/api/gateway/audit-summary").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.serviceStatus.user-service").value("UP"))
            .andExpect(jsonPath("$.data.serviceStatus.admin-service").value("UP"));
    }

    @Test
    @DisplayName("GET /api/gateway/audit-summary propagates X-User-Id header to downstream services")
    void getAuditSummary_propagatesAuthHeader() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(Map.of("code", 200, "data", List.of()), HttpStatus.OK));

        mockMvc.perform(get("/api/gateway/audit-summary").header("X-User-Id", "42"))
            .andExpect(status().isOk());

        // Verify X-User-Id was propagated in the HttpEntity headers
        verify(restTemplate, atLeastOnce())
            .exchange(
                anyString(),
                eq(HttpMethod.GET),
                argThat(entity -> {
                    String userId = entity.getHeaders().getFirst("X-User-Id");
                    return "42".equals(userId);
                }),
                eq(Map.class)
            );
    }

    @Test
    @DisplayName("GET /api/gateway/audit-summary marks DOWN when downstream fails")
    void getAuditSummary_marksDownOnFailure() throws Exception {
        when(restTemplate.exchange(contains("/api/users/audit"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RuntimeException("Connection refused"));
        when(restTemplate.exchange(contains("/api/admin/audit-logs"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(Map.of("code", 200, "data", List.of()), HttpStatus.OK));

        mockMvc.perform(get("/api/gateway/audit-summary").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serviceStatus.user-service").value("DOWN"))
            .andExpect(jsonPath("$.data.serviceStatus.admin-service").value("UP"));
    }

    @Test
    @DisplayName("GET /api/gateway/audit-summary returns 401 when X-User-Id header is missing")
    void getAuditSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/gateway/audit-summary"))
            .andExpect(status().isUnauthorized());
    }
}
