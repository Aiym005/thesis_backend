package com.tms.thesissystem.gateway.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.DatabaseStatusService;
import com.tms.thesissystem.application.service.WorkflowAsyncService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/gateway")
public class GatewayController {
    private final WorkflowQueryService queryService;
    private final WorkflowAsyncService asyncService;
    private final DatabaseStatusService databaseStatusService;
    private final ApiResponseMapper apiResponseMapper;
    private final boolean rabbitEnabled;

    public GatewayController(WorkflowQueryService queryService,
                             WorkflowAsyncService asyncService,
                             DatabaseStatusService databaseStatusService,
                             ApiResponseMapper apiResponseMapper,
                             @Value("${app.messaging.rabbit.enabled:false}") boolean rabbitEnabled) {
        this.queryService = queryService;
        this.asyncService = asyncService;
        this.databaseStatusService = databaseStatusService;
        this.apiResponseMapper = apiResponseMapper;
        this.rabbitEnabled = rabbitEnabled;
    }

    @GetMapping("/dashboard")
    public ApiDtos.DashboardResponse dashboard() {
        return apiResponseMapper.toDashboardResponse(queryService.getDashboard());
    }

    @GetMapping("/dashboard-async")
    public CompletableFuture<ApiDtos.DashboardResponse> dashboardAsync() {
        return asyncService.dashboardAsync();
    }

    @GetMapping("/system-map")
    public GatewaySystemMapResponse systemMap() {
        return new GatewaySystemMapResponse(
                new ServiceNode("api-gateway", "Routes requests to internal bounded-context services"),
                List.of(
                        new ServiceNode("user-service", "Authentication and user directory"),
                        new ServiceNode("topic-service", "Topic proposal, selection, and approval flow"),
                        new ServiceNode("plan-service", "Weekly plan authoring and approvals"),
                        new ServiceNode("review-service", "Weekly review submissions"),
                        new ServiceNode("notification-service", "Notification projection from workflow events"),
                        new ServiceNode("audit-service", "Audit trail projection from workflow events")
                ),
                rabbitEnabled ? "rabbitmq-event-bus" : "in-process-event-bus",
                databaseStatusService.check()
        );
    }

    public record GatewaySystemMapResponse(
            ServiceNode gateway,
            List<ServiceNode> downstreamServices,
            String eventBusMode,
            DatabaseStatusService.DatabaseStatus database
    ) {
    }

    public record ServiceNode(String name, String responsibility) {
    }
}
