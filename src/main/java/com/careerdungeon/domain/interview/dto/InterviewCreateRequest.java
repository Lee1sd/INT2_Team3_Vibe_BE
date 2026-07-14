package com.careerdungeon.domain.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InterviewCreateRequest(
        @NotNull(message = "resumeId는 필수입니다.")
        Long resumeId,

        @NotNull(message = "interviewerId는 필수입니다.")
        Long interviewerId,

        @NotBlank(message = "keyword는 필수입니다.")
        String keyword
) {
}
