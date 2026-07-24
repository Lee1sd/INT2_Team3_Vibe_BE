package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;

/**
 * 최초 네 문항 채점 LLM 원시 응답.
 *
 * <p>ADR-008에 따라 최초 응답에만 최저점 문항 식별자를 둔다. 보고된 파생값은 서버가
 * 루브릭 점수로 다시 계산하므로 존재 여부만 검증하고 판정에는 신뢰하지 않는다.
 *
 * @param evaluations questionId 1~4의 문항별 원시 평가
 * @param totalScore LLM이 보고한 최초 네 문항 총점
 * @param weakestQuestionId LLM이 보고한 최저점 문항
 * @param passed LLM이 보고한 합격 여부
 */
public record RawInitialEvaluationResponse(
        List<RawQuestionEvaluation> evaluations,
        Integer totalScore,
        Integer weakestQuestionId,
        Boolean passed
) {
}
