package com.careerdungeon.global.llm.dto;

import java.util.List;
import java.util.Objects;

/**
 * @param questionAnswerPairs 채점 대상 질문-답변 쌍 목록 (최대 4개)
 * @param personaTone         면접관 톤 — 피드백 문체에 영향
 * @param userName            사용자 이름 — 피드백 개인화 (FR-12)
 */
public record EvaluationRequest(
        List<QuestionAnswerPair> questionAnswerPairs,
        String personaTone,
        String userName
) {
    public EvaluationRequest {
        Objects.requireNonNull(questionAnswerPairs, "questionAnswerPairs must not be null");
    }
}
