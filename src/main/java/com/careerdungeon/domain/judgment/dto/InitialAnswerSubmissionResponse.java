package com.careerdungeon.domain.judgment.dto;

import java.util.List;

/** 최초 세 문항 채점과 꼬리질문 생성 결과를 반환한다. */
public record InitialAnswerSubmissionResponse(
        List<AnswerEvaluationResponse> evaluations,
        int totalScore,
        long weakestQuestionId,
        boolean passed,
        NextTurnResponse nextTurn
) implements AnswerSubmissionResponse {
    /** 응답 목록이 외부에서 변경되지 않도록 복사한다. */
    public InitialAnswerSubmissionResponse {
        evaluations = List.copyOf(evaluations);
    }
}
