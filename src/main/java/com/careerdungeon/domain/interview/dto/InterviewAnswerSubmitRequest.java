package com.careerdungeon.domain.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InterviewAnswerSubmitRequest(
        @NotEmpty(message = "답변 목록은 필수입니다.")
        List<@Valid InterviewAnswerItemRequest> answers
) {
    public InterviewAnswerSubmitRequest {
        answers = answers == null ? null : List.copyOf(answers);
    }
}
