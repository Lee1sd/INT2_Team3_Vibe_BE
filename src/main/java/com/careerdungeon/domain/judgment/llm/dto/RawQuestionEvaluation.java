package com.careerdungeon.domain.judgment.llm.dto;

/** 문항별 LLM 원시 평가. score는 호환 필드이며 서버가 rubricScores로 다시 계산한다. */
public record RawQuestionEvaluation(
        int questionId,
        Integer score,
        RubricScores rubricScores,
        String feedback
) {
}
