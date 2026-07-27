package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;

import java.time.Instant;
import java.util.Objects;

public record ResumeSummaryResponse(
        Long resumeId,
        ResumeType type,
        ParseStatus parseStatus,
        Instant lastUploadedAt,
        String originalFileName,
        Long fileSize,
        boolean isLastUsed
) {
    /**
     * {@code lastUsedResumeId}는 이슈 #173: 사용자의 가장 최근 면접 세션이 쓴 이력서 id(없으면 {@code null}).
     * {@link Objects#equals}로 비교해 어느 쪽이 {@code null}이어도(신규 사용자, 그 이력서가 이후
     * 삭제된 경우 등) NPE 없이 {@code false}로 처리한다.
     */
    public static ResumeSummaryResponse from(Resume resume, Long lastUsedResumeId) {
        return new ResumeSummaryResponse(
                resume.getId(),
                resume.getType(),
                resume.getParseStatus(),
                resume.getLastUploadedAt(),
                resume.getOriginalFileName(),
                resume.getFileSize(),
                Objects.equals(resume.getId(), lastUsedResumeId)
        );
    }
}
