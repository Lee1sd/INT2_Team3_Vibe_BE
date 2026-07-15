package com.careerdungeon.global.llm.dto;

import java.util.List;
import java.util.Objects;

/**
 * @param questionAnswerPairs 채점 대상 질문-답변 쌍 목록 — IS-002 최초 채점은 turn 1~3 (3개),
 *                            IS-002b 최종 채점은 turn 4 한 건
 * @param personaTone         면접관 톤 — 피드백 문체에 영향
 * @param userName            사용자 이름 — 피드백 개인화 (FR-12)
 * @param previousEvaluations 최종 종합 피드백에만 사용하는 최초 turn 1~3 확정 평가 컨텍스트
 */
public record EvaluationRequest(
        List<QuestionAnswerPair> questionAnswerPairs,
        String personaTone,
        String userName,
        List<PreviousEvaluationContext> previousEvaluations
) {
    public EvaluationRequest {
        Objects.requireNonNull(questionAnswerPairs, "questionAnswerPairs must not be null");
        Objects.requireNonNull(previousEvaluations, "previousEvaluations must not be null");
        questionAnswerPairs = List.copyOf(questionAnswerPairs);
        previousEvaluations = List.copyOf(previousEvaluations);
    }

    /** 최초 채점 등 이전 평가 컨텍스트가 필요 없는 호출을 위한 호환 생성자다. */
    public EvaluationRequest(List<QuestionAnswerPair> pairs, String tone, String name) {
        this(pairs, tone, name, List.of());
    }

    /** IS-002 최초 채점 요청 (turn 1~3) */
    public static EvaluationRequest initial(List<QuestionAnswerPair> pairs, String tone, String name) {
        return new EvaluationRequest(pairs, tone, name, List.of());
    }

    /**
     * IS-002b 최종 채점 요청 — turn 4의 질문·답변·모범답안 한 건만 전달한다.
     */
    public static EvaluationRequest finalEvaluation(
            List<QuestionAnswerPair> pairs,
            List<PreviousEvaluationContext> previousEvaluations,
            String tone,
            String name) {
        return new EvaluationRequest(pairs, tone, name, previousEvaluations);
    }
}
