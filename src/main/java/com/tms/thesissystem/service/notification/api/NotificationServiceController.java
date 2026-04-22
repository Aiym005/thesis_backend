package com.tms.thesissystem.service.notification.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationServiceController {
    private final WorkflowQueryService queryService;
    private final ApiResponseMapper apiResponseMapper;

    public NotificationServiceController(WorkflowQueryService queryService, ApiResponseMapper apiResponseMapper) {
        this.queryService = queryService;
        this.apiResponseMapper = apiResponseMapper;
    }

    @GetMapping
    public List<ApiDtos.NotificationDto> notifications(@RequestParam(required = false) Long userId) {
        return queryService.getDashboard().notifications().stream()
                .filter(notification -> userId == null || userId.equals(notification.userId()))
                .map(apiResponseMapper::toNotificationDto)
                .toList();
    }
}
