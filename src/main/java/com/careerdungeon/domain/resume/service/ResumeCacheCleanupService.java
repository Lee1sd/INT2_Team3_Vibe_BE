package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ResumeCacheCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ResumeCacheCleanupService.class);

    private final ResumeRepository resumeRepository;

    public ResumeCacheCleanupService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public int expireResumes() {
        int expiredCount = resumeRepository.expireResumes(
                Instant.now(), ParseStatus.DONE, ParseStatus.EXPIRED);
        log.info("만료된 이력서/포트폴리오 캐시 정리 완료 (처리 건수={})", expiredCount);
        return expiredCount;
    }
}
