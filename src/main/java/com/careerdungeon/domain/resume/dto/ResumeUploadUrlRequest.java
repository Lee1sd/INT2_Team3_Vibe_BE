package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ResumeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ResumeUploadUrlRequest(
        @NotNull ResumeType type,
        @NotBlank String fileName,
        @Positive long fileSize,
        @NotBlank String contentType
) {
}
