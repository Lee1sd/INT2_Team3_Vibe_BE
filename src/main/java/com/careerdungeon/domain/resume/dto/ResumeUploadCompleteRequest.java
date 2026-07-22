package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ResumeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResumeUploadCompleteRequest(
        @NotNull ResumeType type,
        @NotBlank String s3Key
) {
}
