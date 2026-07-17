package com.careerdungeon.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewAnswerSubmitResponse(
        List<InterviewAnswerEvaluationResponse> evaluations,
        int totalScore,
        Integer weakestQuestionId,
        boolean passed,
        String overallFeedback,
        InterviewNextTurnResponse nextTurn
) {
    public InterviewAnswerSubmitResponse {
        evaluations = List.copyOf(evaluations);
    }
}
