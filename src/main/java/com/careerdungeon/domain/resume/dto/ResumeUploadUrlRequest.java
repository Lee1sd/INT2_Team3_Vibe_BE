package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ResumeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ResumeUploadUrlRequest(
        @NotNull ResumeType type,
        @NotBlank @Size(max = 255) String fileName,
        @Positive long fileSize,
        @NotBlank String contentType
) {
}
