package com.careerdungeon.domain.judgment.model;

/** 서버에서 루브릭 항목을 clamp한 뒤 확정한 문항 점수 값. */
public record QuestionScore(
        int questionId,
        int score,
        String feedback
) {
}
