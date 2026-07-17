package com.careerdungeon.domain.judgment.dto;

import java.util.List;

/** 최초 확정 점수와 turn 4 점수를 합친 최종 판정 결과를 반환한다. */
public record FinalAnswerSubmissionResponse(
        List<AnswerEvaluationResponse> evaluations,
        int totalScore,
        boolean passed,
        String overallFeedback,
        NextTurnResponse nextTurn
) implements AnswerSubmissionResponse {
    /** 응답 목록이 외부에서 변경되지 않도록 복사한다. */
    public FinalAnswerSubmissionResponse {
        evaluations = List.copyOf(evaluations);
    }
}
