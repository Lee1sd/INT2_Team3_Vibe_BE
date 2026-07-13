package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;
import java.util.Objects;

/** 채점 Mock 또는 향후 Claude 어댑터에 전달할 요청. */
public record EvaluationRequest(
        List<QuestionAnswerPair> questionAnswerPairs,
        String personaTone,
        String userName
) {
    /**
     * 외부에서 전달된 목록이 이후 변경되지 않도록 방어적 복사한다.
     */
    public EvaluationRequest {
        Objects.requireNonNull(questionAnswerPairs, "질문-답변 목록은 필수입니다.");
        questionAnswerPairs = List.copyOf(questionAnswerPairs);
    }
}
