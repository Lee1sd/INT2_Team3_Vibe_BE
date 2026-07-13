package com.careerdungeon.domain.judgment.llm.dto;

/**
 * 문항별 LLM 원시 평가. score는 선택 호환 필드이며 서버가 rubricScores로 다시 계산한다.
 *
 * @param questionId 평가 대상 문항 식별자
 * @param score LLM이 보고한 문항 합계 점수. 누락되어도 서버 판정에는 영향 없음
 * @param rubricScores LLM이 보고한 5개 루브릭 원시 점수
 * @param feedback 사용자에게 제공할 문항별 피드백
 */
public record RawQuestionEvaluation(
        int questionId,
        Integer score,
        RubricScores rubricScores,
        String feedback
) {
}
