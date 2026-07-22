package com.careerdungeon.domain.resume.service;

public record PresignedResumeUpload(
        String uploadUrl,
        String s3Key,
        long expiresInSeconds
) {
}
