package com.careerdungeon.domain.interview.dto;

public record InterviewNextTurnResponse(
        String type,
        Integer targetQuestionId,
        String question
) {
}
