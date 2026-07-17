package com.careerdungeon.domain.interview.dto;

public record InterviewAnswerEvaluationResponse(
        Long questionId,
        int score,
        String feedback
) {
}
