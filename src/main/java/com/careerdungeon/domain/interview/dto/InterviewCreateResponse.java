package com.careerdungeon.domain.interview.dto;

import java.util.List;

public record InterviewCreateResponse(
        Long sessionId,
        String status,
        List<InterviewQuestionResponse> questions
) {
}
