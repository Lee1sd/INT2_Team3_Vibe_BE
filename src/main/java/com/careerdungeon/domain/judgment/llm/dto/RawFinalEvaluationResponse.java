package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;

/**
 * 꼬리질문 한 문항에 대한 최종 채점 LLM 원시 응답.
 *
 * <p>ADR-008에 따라 최종 응답에는 최저점 문항 식별자를 두지 않는다. 최종 채점은
 * questionId 4만 포함하며, 서버가 보존한 최초 1~3 점수와 합쳐 최종 판정을 만든다.
 *
 * @param evaluations questionId 4의 원시 평가
 * @param totalScore LLM이 보고한 꼬리질문 점수 합계. 서버 최종 합산에는 신뢰하지 않는다
 * @param passed LLM이 보고한 합격 여부. 서버 최종 판정에는 신뢰하지 않는다
 * @param overallFeedback 최초 1~3 읽기 전용 평가 컨텍스트와 꼬리질문을 반영한 종합 피드백
 */
public record RawFinalEvaluationResponse(
        List<RawQuestionEvaluation> evaluations,
        Integer totalScore,
        Boolean passed,
        String overallFeedback
) {
}
