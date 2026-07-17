package com.careerdungeon.domain.interview.dto;

public record InterviewAnswerEvaluationResponse(
        Integer questionId,
        int score,
        String feedback
) {
}
