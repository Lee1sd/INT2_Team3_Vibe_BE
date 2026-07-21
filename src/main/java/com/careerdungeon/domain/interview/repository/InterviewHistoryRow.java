package com.careerdungeon.domain.interview.repository;

import java.time.Instant;

public record InterviewHistoryRow(
        int level,
        Long sessionId,
        Instant createdAt,
        Integer totalScore
) {
}
