package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;

import java.time.Instant;

public record ResumeSummaryResponse(
        Long resumeId,
        ResumeType type,
        ParseStatus parseStatus,
        Instant createdAt
) {
    public static ResumeSummaryResponse from(Resume resume) {
        return new ResumeSummaryResponse(
                resume.getId(),
                resume.getType(),
                resume.getParseStatus(),
                resume.getCreatedAt()
        );
    }
}
