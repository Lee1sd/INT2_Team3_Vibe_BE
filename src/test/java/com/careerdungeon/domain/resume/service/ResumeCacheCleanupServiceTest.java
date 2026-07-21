package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(ResumeCacheCleanupService.class)
class ResumeCacheCleanupServiceTest {

    @Autowired
    ResumeCacheCleanupService cleanupService;

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("만료된 Resume을 EXPIRED로 전환해 레코드와 면접 히스토리는 유지하고 슬롯은 반환한다")
    void expireResumes_preservesRecordAndReleasesSlot() {
        Instant expiredAt = Instant.parse("2020-01-01T00:00:00Z");
        Resume expired = doneResume("expired text", expiredAt);
        Resume notExpired = doneResume("active text", Instant.parse("2100-01-01T00:00:00Z"));
        Resume withoutExpiration = doneResume("no expiration", null);
        Resume processing = new Resume(2L, ResumeType.PORTFOLIO, "portfolio.md", "hash-2");
        resumeRepository.saveAllAndFlush(List.of(expired, notExpired, withoutExpiration, processing));

        Long expiredId = expired.getId();
        Long notExpiredId = notExpired.getId();
        Long withoutExpirationId = withoutExpiration.getId();
        Long processingId = processing.getId();
        int firstExpiredCount = cleanupService.expireResumes();
        int secondExpiredCount = cleanupService.expireResumes();
        entityManager.clear();

        assertThat(firstExpiredCount).isEqualTo(1);
        assertThat(secondExpiredCount).isZero();

        Resume expiredResult = resumeRepository.findById(expiredId).orElseThrow();
        assertThat(expiredResult.getParseStatus()).isEqualTo(ParseStatus.EXPIRED);
        assertThat(expiredResult.getExtractedText()).isNull();
        assertThat(expiredResult.getCacheExpiresAt()).isEqualTo(expiredAt);
        assertThat(resumeRepository.countByUserIdAndTypeAndParseStatusNotIn(
                1L,
                ResumeType.RESUME,
                Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED))).isEqualTo(2L);

        assertThat(resumeRepository.findById(notExpiredId).orElseThrow().getExtractedText())
                .isEqualTo("active text");
        assertThat(resumeRepository.findById(withoutExpirationId).orElseThrow().getParseStatus())
                .isEqualTo(ParseStatus.DONE);
        assertThat(resumeRepository.findById(withoutExpirationId).orElseThrow().getExtractedText())
                .isEqualTo("no expiration");
        assertThat(resumeRepository.findById(processingId).orElseThrow().getParseStatus())
                .isEqualTo(ParseStatus.PROCESSING);
    }

    private Resume doneResume(String text, Instant cacheExpiresAt) {
        Resume resume = new Resume(1L, ResumeType.RESUME, "resume.pdf", "hash");
        resume.markDone(text, cacheExpiresAt);
        return resume;
    }
}
