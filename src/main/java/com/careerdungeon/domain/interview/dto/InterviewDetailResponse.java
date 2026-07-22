package com.careerdungeon.domain.interview.dto;

import java.util.List;

public record InterviewDetailResponse(
        Long sessionId,
        List<InterviewDetailMessageResponse> messages,
        String overallFeedback
) {
}
