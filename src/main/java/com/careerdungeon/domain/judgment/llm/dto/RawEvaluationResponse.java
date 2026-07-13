package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;

/**
 * 채점 LLM 원시 응답 스키마. 상위 필드는 기존 평가 계약을 유지하되 서버 판정에는 신뢰하지 않는다.
 */
public record RawEvaluationResponse(
        List<RawQuestionEvaluation> evaluations,
        Integer totalScore,
        Integer weakestQuestionId,
        Boolean passed,
        String overallFeedback
) {
}
