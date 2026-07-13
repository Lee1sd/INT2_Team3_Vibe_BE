package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;

/**
 * 채점 LLM 원시 응답 스키마. 상위 필드는 기존 평가 계약을 유지하되 서버 판정에는 신뢰하지 않는다.
 *
 * @param evaluations 문항별 원시 평가 목록
 * @param totalScore LLM이 보고한 총점
 * @param weakestQuestionId LLM이 보고한 최저점 문항
 * @param passed LLM이 보고한 합격 여부
 * @param overallFeedback 전체 답변에 대한 종합 피드백
 */
public record RawEvaluationResponse(
        List<RawQuestionEvaluation> evaluations,
        Integer totalScore,
        Integer weakestQuestionId,
        Boolean passed,
        String overallFeedback
) {
}
