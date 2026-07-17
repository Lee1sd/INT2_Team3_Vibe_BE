package com.careerdungeon.domain.interview.dto;

public record InterviewAnswerEvaluationResponse(
        int questionId,
        int score,
        String feedback
) {
}
