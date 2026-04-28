package com.tms.thesissystem.domain;

import java.time.LocalDateTime;

public record Notification(
        Long id,
        Long userId,
        String title,
        String message,
        LocalDateTime createdAt
) {
}
