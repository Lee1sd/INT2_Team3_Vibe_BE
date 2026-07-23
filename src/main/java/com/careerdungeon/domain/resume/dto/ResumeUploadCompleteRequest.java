package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ResumeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResumeUploadCompleteRequest(
        @NotNull ResumeType type,
        @NotBlank String s3Key,
        @NotBlank @Size(max = 255) String originalFileName
) {
}
