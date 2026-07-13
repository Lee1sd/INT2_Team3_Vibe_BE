package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;
import java.util.Objects;

/** 채점 Mock 또는 향후 Claude 어댑터에 전달할 요청. */
public record EvaluationRequest(
        List<QuestionAnswerPair> questionAnswerPairs,
        String personaTone,
        String userName
) {
    public EvaluationRequest {
        Objects.requireNonNull(questionAnswerPairs, "questionAnswerPairs must not be null");
        questionAnswerPairs = List.copyOf(questionAnswerPairs);
    }
}
