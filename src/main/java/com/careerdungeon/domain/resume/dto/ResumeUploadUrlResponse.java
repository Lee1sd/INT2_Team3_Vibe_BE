package com.careerdungeon.domain.resume.dto;

public record ResumeUploadUrlResponse(
        String uploadUrl,
        String s3Key,
        long expiresInSeconds
) {
}
