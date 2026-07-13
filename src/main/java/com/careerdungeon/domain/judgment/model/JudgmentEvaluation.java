package com.careerdungeon.domain.judgment.model;

import java.util.List;

/** 영속화 전 단계의 서버 확정 채점 결과. */
public record JudgmentEvaluation(
        List<QuestionScore> evaluations,
        int totalScore,
        int weakestQuestionId,
        boolean passed,
        String overallFeedback
) {
    public JudgmentEvaluation {
        evaluations = List.copyOf(evaluations);
    }
}
