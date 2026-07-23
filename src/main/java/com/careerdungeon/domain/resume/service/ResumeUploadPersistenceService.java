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

    @Transactional
    public ResumeResponse persist(Long userId, ResumeType type, String s3Key, String fileHash, String s3Etag) {
        capacityPolicy.ensureAvailableWithUserLock(userId, type);

        Resume resume = resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                        userId, type, ParseStatus.FAILED)
                .map(failed -> {
                    failed.replaceUpload(s3Key, fileHash, s3Etag);
                    return failed;
                })
                .orElseGet(() -> resumeRepository.save(new Resume(userId, type, s3Key, fileHash, s3Etag)));
        eventPublisher.publishEvent(new ResumeUploadedEvent(resume.getId()));
        return ResumeResponse.uploaded(resume.getId(), resume.getType(), resume.getParseStatus());
    }
}
