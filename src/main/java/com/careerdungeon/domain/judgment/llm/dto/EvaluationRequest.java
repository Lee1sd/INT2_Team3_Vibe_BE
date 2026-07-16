package com.careerdungeon.domain.judgment.llm.dto;

import java.util.List;
import java.util.Objects;

/** 채점 Mock 또는 향후 Claude 어댑터에 전달할 요청. */
public record EvaluationRequest(
        List<QuestionAnswerPair> questionAnswerPairs,
        String personaTone,
        String userName,
        List<PreviousEvaluationContext> previousEvaluations
) {
    /**
     * 외부에서 전달된 목록이 이후 변경되지 않도록 방어적 복사한다.
     */
    public EvaluationRequest {
        Objects.requireNonNull(questionAnswerPairs, "질문-답변 목록은 필수입니다.");
        Objects.requireNonNull(previousEvaluations, "이전 평가 컨텍스트 목록은 필수입니다.");
        questionAnswerPairs = List.copyOf(questionAnswerPairs);
        previousEvaluations = List.copyOf(previousEvaluations);
    }

    /** 최초 채점처럼 이전 평가 컨텍스트가 없는 호출을 위한 호환 생성자다. */
    public EvaluationRequest(List<QuestionAnswerPair> pairs, String tone, String name) {
        this(pairs, tone, name, List.of());
    }
}
