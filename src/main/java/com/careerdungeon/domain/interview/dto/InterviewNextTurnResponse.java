package com.careerdungeon.domain.interview.dto;

public record InterviewNextTurnResponse(
        String type,
        Long targetQuestionId,
        String question
) {
}
