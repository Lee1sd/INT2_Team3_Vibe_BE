package com.careerdungeon.domain.interview.dto;

import java.time.LocalDateTime;

public record InterviewHistorySessionResponse(
        Long sessionId,
        LocalDateTime createdAt,
        Integer totalScore
) {
}
