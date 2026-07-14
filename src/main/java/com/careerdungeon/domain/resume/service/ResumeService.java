package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ResumeService {

    private static final int MAX_RESUME_PER_TYPE = 3;

    private final ResumeRepository resumeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ResumeService(ResumeRepository resumeRepository, ApplicationEventPublisher eventPublisher) {
        this.resumeRepository = resumeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ResumeResponse upload(Long userId, ResumeType type, MultipartFile file) {
        long uploadedCount = resumeRepository.countByUserIdAndType(userId, type);
        if (uploadedCount >= MAX_RESUME_PER_TYPE) {
            throw new ResumeTypeLimitExceededException(type);
        }

        // TODO: S3에 원본 파일 업로드 후 실제 s3Key 발급
        String s3Key = null;

        // TODO: 파일 바이트 기반 해시(SHA-256 등) 계산
        String fileHash = null;

        Resume resume = new Resume(userId, type, s3Key, fileHash);
        resumeRepository.save(resume);

        eventPublisher.publishEvent(new ResumeUploadedEvent(resume.getId()));

        return ResumeResponse.uploaded(resume.getId(), resume.getType(), resume.getParseStatus());
    }

    public ResumeResponse getStatus(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        return ResumeResponse.of(resume);
    }
}
