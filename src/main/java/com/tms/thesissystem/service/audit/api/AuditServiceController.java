package com.tms.thesissystem.service.audit.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audits")
public class AuditServiceController {
    private final WorkflowQueryService queryService;
    private final ApiResponseMapper apiResponseMapper;

    public AuditServiceController(WorkflowQueryService queryService, ApiResponseMapper apiResponseMapper) {
        this.queryService = queryService;
        this.apiResponseMapper = apiResponseMapper;
    }

    @GetMapping
    public List<ApiDtos.AuditEntryDto> audits(@RequestParam(required = false) String entityType) {
        return queryService.getDashboard().auditTrail().stream()
                .filter(audit -> entityType == null || entityType.equalsIgnoreCase(audit.entityType()))
                .map(apiResponseMapper::toAuditEntryDto)
                .toList();
    }
}
