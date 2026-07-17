package com.careerdungeon.domain.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InterviewAnswerItemRequest(
        @NotNull(message = "questionId는 필수입니다.")
        Integer questionId,

        @NotBlank(message = "답변 내용은 필수입니다.")
        String answerText
) {
}
