package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;

public record ResumeResponse(
        Long resumeId,
        ResumeType type,
        ParseStatus parseStatus,
        String extractedText,
        String originalFileName,
        Long fileSize

) {
    // RS-001용: 업로드 직후 응답 (extractedText 없음)
    public static ResumeResponse uploaded(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getType(),
                resume.getParseStatus(),
                null,
                resume.getOriginalFileName(),
                resume.getFileSize()
        );
    }

    public static ResumeResponse uploaded(Long resumeId, ResumeType type, ParseStatus parseStatus) {
        return new ResumeResponse(resumeId, type, parseStatus, null, null, null);
    }

    // RS-002용: 폴링 조회 응답 (extractedText 포함 가능)
    public static ResumeResponse of(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getType(),
                resume.getParseStatus(),
                resume.getExtractedText(),
                resume.getOriginalFileName(),
                resume.getFileSize()
        );
    }

}
