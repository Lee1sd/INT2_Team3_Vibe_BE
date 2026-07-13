package com.careerdungeon.global.llm.dto;

import java.util.List;
import java.util.Objects;

/**
 * @param questionAnswerPairs 채점 대상 질문-답변 쌍 목록 — IS-002 최초 채점은 turn 1~3 (3개),
 *                            IS-002b 최종 채점은 turn 1~4 전체 (4개, 꼬리질문 포함)
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

    /** IS-002 최초 채점 요청 (turn 1~3) */
    public static EvaluationRequest initial(List<QuestionAnswerPair> pairs, String tone, String name) {
        return new EvaluationRequest(pairs, tone, name);
    }

    /**
     * IS-002b 꼬리질문 포함 최종 채점 요청 — 최초 3문항과 꼬리질문을 합친 turn 1~4 전체를
     * 질문·답변·모범답안과 함께 전달한다(ADR-010 — judgment의 EvaluationLlmClient가 요구하는
     * 형태와 동일).
     */
    public static EvaluationRequest finalEvaluation(List<QuestionAnswerPair> pairs, String tone, String name) {
        return new EvaluationRequest(pairs, tone, name);
    }
}
