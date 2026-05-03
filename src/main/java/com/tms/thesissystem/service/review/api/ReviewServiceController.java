package com.tms.thesissystem.service.review.api;

import com.tms.thesissystem.api.ApiDtos;
import com.tms.thesissystem.api.ApiResponseMapper;
import com.tms.thesissystem.application.service.WorkflowCommandService;
import com.tms.thesissystem.application.service.WorkflowQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewServiceController {
    private final WorkflowQueryService queryService;
    private final WorkflowCommandService commandService;
    private final ApiResponseMapper apiResponseMapper;

    @GetMapping
    public List<ApiDtos.ReviewDto> reviews() {
        return queryService.getDashboard().reviews().stream()
                .map(apiResponseMapper::toReviewDto)
                .toList();
    }

    @PostMapping
    public ReviewSubmissionResponse submitReview(@RequestBody ReviewRequest request) {
        return new ReviewSubmissionResponse(
                apiResponseMapper.toReviewDto(commandService.submitReview(
                        request.planId(), request.reviewerId(), request.week(), request.score(), request.comment())),
                apiResponseMapper.toWorkflowStateResponse(queryService.getDashboard())
        );
    }

    public record ReviewRequest(Long planId, Long reviewerId, int week, int score, String comment) {
    }

    public record ReviewSubmissionResponse(ApiDtos.ReviewDto review, ApiDtos.WorkflowStateResponse state) {
    }
}
