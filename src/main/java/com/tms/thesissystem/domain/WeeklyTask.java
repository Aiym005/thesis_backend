package com.tms.thesissystem.domain;

public record WeeklyTask(
        int week,
        String title,
        String deliverable,
        String focus
) {
}
