package com.careerdungeon.domain.interview.dto;

import java.time.Instant;

public record InterviewHistorySessionResponse(
        Long sessionId,
        Instant createdAt,
        Integer totalScore
) {
}
