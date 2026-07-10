package com.careerdungeon.global.llm.dto;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @param questionAnswerPairs 채점 대상 질문-답변 쌍 목록
 * @param personaTone         면접관 톤 — 피드백 문체에 영향
 * @param userName            사용자 이름 — 피드백 개인화 (FR-12)
 * @param retainedTurns       IS-002b 최종 채점 시 유지된 원본 문항 turn 집합. IS-002 초기 채점은 null.
 */
public record EvaluationRequest(
        List<QuestionAnswerPair> questionAnswerPairs,
        String personaTone,
        String userName,
        Set<Integer> retainedTurns
) {
    public EvaluationRequest {
        Objects.requireNonNull(questionAnswerPairs, "questionAnswerPairs must not be null");
    }

    /** IS-002 최초 채점 요청 */
    public static EvaluationRequest initial(List<QuestionAnswerPair> pairs, String tone, String name) {
        return new EvaluationRequest(pairs, tone, name, null);
    }

    /**
     * IS-002b 꼬리질문 최종 채점 요청.
     * retainedTurns는 IS-002 응답의 weakestQuestionId를 제외한 원본 turn 집합 — 호출자가 전달.
     */
    public static EvaluationRequest followUp(
            QuestionAnswerPair followUpPair, String tone, String name, Set<Integer> retainedTurns) {
        return new EvaluationRequest(List.of(followUpPair), tone, name, retainedTurns);
    }
}
