package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeUploadPersistenceService {
    private final ResumeRepository resumeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ResumeCapacityPolicy capacityPolicy;

    public ResumeUploadPersistenceService(ResumeRepository resumeRepository,
                                          ApplicationEventPublisher eventPublisher,
                                          ResumeCapacityPolicy capacityPolicy) {
        this.resumeRepository = resumeRepository;
        this.eventPublisher = eventPublisher;
        this.capacityPolicy = capacityPolicy;
    }

    /**
     * {@code requestedFileName}은 이번 요청에서 클라이언트가 보낸(검증 완료) 파일명이며 없으면 {@code null}이다.
     * {@code fallbackFileName}은 재사용할 이전 값도 없을 때만 쓰는 {@code type} 기준 기본값이다.
     * 재업로드(FAILED 슬롯 교체)는 새 이름이 없으면 기존 {@code originalFileName}을 그대로 유지한다 —
     * 이름을 안 보냈다고 해서 이미 있던 의미 있는 이름을 지우지 않는다.
     */
    @Transactional
    public ResumeResponse persist(Long userId, ResumeType type, String s3Key, String fileHash, String s3Etag,
                                  String requestedFileName, String fallbackFileName, long fileSize) {
        capacityPolicy.ensureAvailableWithUserLock(userId, type);

        Resume resume = resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                        userId, type, ParseStatus.FAILED)
                .map(failed -> {
                    String effectiveFileName = requestedFileName != null
                            ? requestedFileName
                            : (failed.getOriginalFileName() != null ? failed.getOriginalFileName() : fallbackFileName);
                    failed.replaceUpload(s3Key, fileHash, s3Etag, effectiveFileName, fileSize);
                    return failed;
                })
                .orElseGet(() -> resumeRepository.save(
                        new Resume(userId, type, s3Key, fileHash, s3Etag,
                                requestedFileName != null ? requestedFileName : fallbackFileName, fileSize)));
        eventPublisher.publishEvent(new ResumeUploadedEvent(resume.getId()));
        return ResumeResponse.uploaded(resume);
    }
}
