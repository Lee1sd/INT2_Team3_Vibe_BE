package com.careerdungeon.domain.resume.dto;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;

import java.time.Instant;

// TODO: 이력서 전체 목록 조회 API(추가 예정, 아직 api-spec.md에 미등록)에서 사용할 응답 DTO.
// 아직 해당 API가 없어 미사용 상태이며, 목록 조회 컨트롤러 구현 시 이 DTO를 연결한다.
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