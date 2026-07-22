package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ResumeUploadPersistenceService {
    private static final int MAX_RESUME_PER_TYPE = 3;
    private final ResumeRepository resumeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ResumeUploadPersistenceService(ResumeRepository resumeRepository,
                                          ApplicationEventPublisher eventPublisher) {
        this.resumeRepository = resumeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ResumeResponse persist(Long userId, ResumeType type, String s3Key, String fileHash) {
        long count = resumeRepository.countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
                userId, type, Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED));
        if (count >= MAX_RESUME_PER_TYPE) throw new ResumeTypeLimitExceededException(type);

        Resume resume = resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                        userId, type, ParseStatus.FAILED)
                .map(failed -> {
                    failed.replaceUpload(s3Key, fileHash);
                    return failed;
                })
                .orElseGet(() -> resumeRepository.save(new Resume(userId, type, s3Key, fileHash)));
        eventPublisher.publishEvent(new ResumeUploadedEvent(resume.getId()));
        return ResumeResponse.uploaded(resume.getId(), resume.getType(), resume.getParseStatus());
    }
}
