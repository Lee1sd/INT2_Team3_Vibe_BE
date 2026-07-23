package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ResumeParsingPersistenceService {
    private final ResumeRepository resumeRepository;

    public ResumeParsingPersistenceService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @Transactional
    public int markDoneIfActive(Long resumeId, String extractedText, Instant cacheExpiresAt) {
        return resumeRepository.updateParseResultIfActive(
                resumeId, extractedText, ParseStatus.DONE, cacheExpiresAt);
    }

    @Transactional
    public int markFailedIfActive(Long resumeId) {
        return resumeRepository.updateParseResultIfActive(
                resumeId, null, ParseStatus.FAILED, null);
    }
}
