package com.careerdungeon.domain.interview.dto;

public record InterviewDetailMessageResponse(
        int turn,
        String question,
        String answer
) {
}
