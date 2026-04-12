package com.tms.thesissystem.application.service;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowAsyncServiceTest {

    private final WorkflowQueryService queryService = mock(WorkflowQueryService.class);
    private final ApiResponseMapper mapper = new ApiResponseMapper();
    private final WorkflowAsyncService service = new WorkflowAsyncService(queryService, mapper);

    @Test
    void returnsMappedDashboardResponse() {
        WorkflowQueryService.DashboardSnapshot snapshot = new WorkflowQueryService.DashboardSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new WorkflowQueryService.Summary(2, 3, 4)
        );
        when(queryService.getDashboard()).thenReturn(snapshot);

        ApiDtos.DashboardResponse response = service.dashboardAsync().join();

        assertThat(response.summary().pendingTopics()).isEqualTo(2);
        assertThat(response.summary().pendingPlans()).isEqualTo(3);
        assertThat(response.summary().totalReviews()).isEqualTo(4);
    }
}
