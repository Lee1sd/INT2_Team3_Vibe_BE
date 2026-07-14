package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;

/**
 * 최초 세 문항과 꼬리질문을 포함한 최종 채점 LLM 원시 응답.
 *
 * <p>ADR-008에 따라 최종 응답에는 최저점 문항 식별자를 두지 않는다. 최종 채점은
 * questionId 1~4를 모두 포함해 100점 만점과 종합 피드백을 만든다.
 *
 * @param evaluations questionId 1~4의 문항별 원시 평가
 * @param totalScore LLM이 보고한 네 문항 총점
 * @param passed LLM이 보고한 합격 여부
 * @param overallFeedback 네 문항 전체에 대한 종합 피드백
 */
public record RawFinalEvaluationResponse(
        List<RawQuestionEvaluation> evaluations,
        Integer totalScore,
        Boolean passed,
        String overallFeedback
) {
}
