package com.careerdungeon.domain.judgment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 사용자가 제출한 질문 식별자와 답변 본문을 전달한다. */
public record SubmittedAnswerRequest(
        @NotNull(message = "questionId는 필수입니다.") Long questionId,
        @NotBlank(message = "answerText는 필수입니다.") String answerText
) {
}
