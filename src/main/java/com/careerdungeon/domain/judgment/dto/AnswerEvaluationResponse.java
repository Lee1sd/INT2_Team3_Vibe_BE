package com.careerdungeon.domain.judgment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 질문 메시지 식별자와 서버 확정 점수·피드백을 반환한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerEvaluationResponse(
        long questionId,
        int score,
        String feedback
) {
}
