package com.octopus.demo.dto;

import com.octopus.demo.common.audit.AuditEvent;

import java.util.List;
import java.util.Map;

public record AuditSummaryResponse(
    int userServiceCount,
    int adminServiceCount,
    int totalCount,
    List<AuditEvent> userServiceEvents,
    List<AuditEvent> adminServiceEvents,
    Map<String, String> serviceStatus
) {}
