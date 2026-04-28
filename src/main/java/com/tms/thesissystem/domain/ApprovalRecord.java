package com.tms.thesissystem.domain;

import java.time.LocalDateTime;

public record ApprovalRecord(
        ApprovalStage stage,
        Long actorId,
        String actorName,
        boolean approved,
        String note,
        LocalDateTime decidedAt
) {
}
