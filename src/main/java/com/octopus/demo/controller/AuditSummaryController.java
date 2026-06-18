package com.octopus.demo.controller;

import com.octopus.demo.common.audit.AuditEvent;
import com.octopus.demo.common.auth.RequireAuth;
import com.octopus.demo.common.auth.UserContext;
import com.octopus.demo.common.bean.R;
import com.octopus.demo.dto.AuditSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Gateway 审计聚合端点。
 * 需要 X-User-Id 请求头（由 @RequireAuth 强制执行），防止未授权访问审计数据。
 */
@RequireAuth
@RestController
@RequestMapping("/api/gateway/audit-summary")
public class AuditSummaryController {
    private static final Logger log = LoggerFactory.getLogger(AuditSummaryController.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String userServiceUrl;
    private final String adminServiceUrl;

    public AuditSummaryController(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${services.user-service.base-url}") String userServiceUrl,
            @Value("${services.admin-service.base-url}") String adminServiceUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userServiceUrl = userServiceUrl;
        this.adminServiceUrl = adminServiceUrl;
    }

    @GetMapping
    public R<AuditSummaryResponse> getAuditSummary(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") int limit) {

        int clampedLimit = Math.max(1, Math.min(limit, 500));

        // Capture ThreadLocal context before async execution
        Long currentUserId = UserContext.getUserId();

        // Parallel downstream calls to reduce worst-case latency
        String userUrl = buildUrl(userServiceUrl, "/api/users/audit", userId, action, entityType, from, to, clampedLimit);
        String adminUrl = buildUrl(adminServiceUrl, "/api/admin/audit-logs", userId, action, entityType, from, to, clampedLimit);

        CompletableFuture<ServiceCallResult> userFuture = CompletableFuture.supplyAsync(() -> fetchAuditEvents(userUrl, currentUserId));
        CompletableFuture<ServiceCallResult> adminFuture = CompletableFuture.supplyAsync(() -> fetchAuditEvents(adminUrl, currentUserId));
        CompletableFuture.allOf(userFuture, adminFuture).join();

        ServiceCallResult userResult = userFuture.join();
        ServiceCallResult adminResult = adminFuture.join();

        AuditSummaryResponse summary = new AuditSummaryResponse(
            userResult.events().size(),
            adminResult.events().size(),
            userResult.events().size() + adminResult.events().size(),
            userResult.events(),
            adminResult.events(),
            Map.of(
                "user-service", userResult.available() ? "UP" : "DOWN",
                "admin-service", adminResult.available() ? "UP" : "DOWN"
            )
        );

        return R.ok(summary);
    }

    /**
     * 从下游服务获取审计事件，转发 X-User-Id 认证头。
     * 下游不可用时返回空列表并标记 available=false。
     * 异常分类处理：4xx/5xx/连接超时 降级，其他异常上抛。
     */
    private ServiceCallResult fetchAuditEvents(String url, Long currentUserId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (currentUserId != null) {
                headers.set("X-User-Id", currentUserId.toString());
            }
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, MAP_TYPE);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List<?> list) {
                    List<AuditEvent> events = list.stream()
                        .map(item -> objectMapper.convertValue(item, AuditEvent.class))
                        .toList();
                    return new ServiceCallResult(events, true);
                }
            }
            return new ServiceCallResult(List.of(), true);
        } catch (HttpClientErrorException e) {
            log.warn("下游返回 4xx [{}]: {}", url, e.getStatusCode());
            return new ServiceCallResult(List.of(), false);
        } catch (HttpServerErrorException e) {
            log.warn("下游返回 5xx [{}]: {}", url, e.getStatusCode());
            return new ServiceCallResult(List.of(), false);
        } catch (ResourceAccessException e) {
            log.warn("下游不可达 [{}]: {}", url, e.getMessage());
            return new ServiceCallResult(List.of(), false);
        } catch (Exception e) {
            log.error("未知错误获取审计事件 [{}]", url, e);
            return new ServiceCallResult(List.of(), false);
        }
    }

    private String buildUrl(String baseUrl, String path, Long userId, String action,
                            String entityType, Instant from, Instant to, int limit) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path(path)
            .queryParam("limit", limit);
        if (userId != null) builder.queryParam("userId", userId);
        if (action != null) builder.queryParam("action", action);
        if (entityType != null) builder.queryParam("entityType", entityType);
        if (from != null) builder.queryParam("from", from.toString());
        if (to != null) builder.queryParam("to", to.toString());
        return builder.build().toUriString();
    }

    private record ServiceCallResult(List<AuditEvent> events, boolean available) {}
}
