package com.careerdungeon.domain.resume.repository;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ResumeRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("사용자의 PROCESSING/DONE/FAILED 이력서만 lastUploadedAt 내림차순으로 조회한다")
    void findByUserIdOrderByLastUploadedAtDesc_returnsAllStatusesForUserInDescendingOrder() {
        Resume processing = resume(USER_ID, ParseStatus.PROCESSING, Instant.parse("2026-07-16T01:00:00Z"));
        Resume done = resume(USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T02:00:00Z"));
        Resume failed = resume(USER_ID, ParseStatus.FAILED, Instant.parse("2026-07-16T03:00:00Z"));
        Resume otherUser = resume(OTHER_USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T04:00:00Z"));
        resumeRepository.saveAllAndFlush(List.of(processing, done, failed, otherUser));
        entityManager.clear();

        List<Resume> results = resumeRepository.findByUserIdOrderByLastUploadedAtDesc(USER_ID);

        assertThat(results).extracting(Resume::getUserId)
                .containsOnly(USER_ID);
        assertThat(results).extracting(Resume::getParseStatus)
                .containsExactly(ParseStatus.FAILED, ParseStatus.DONE, ParseStatus.PROCESSING);
        assertThat(results).extracting(Resume::getLastUploadedAt)
                .containsExactly(
                        Instant.parse("2026-07-16T03:00:00Z"),
                        Instant.parse("2026-07-16T02:00:00Z"),
                        Instant.parse("2026-07-16T01:00:00Z"));
    }

    @Test
    @DisplayName("FAILED 이력서 재업로드 시 lastUploadedAt 변경이 DB에 저장되어 최신 항목으로 조회된다")
    void replaceUpload_persistsLastUploadedAtAndMovesResumeToFirst() {
        Instant failedLastUploadedAt = Instant.parse("2026-07-01T00:00:00Z");
        Resume failed = resume(USER_ID, ParseStatus.FAILED, failedLastUploadedAt);
        Resume existing = resume(USER_ID, ParseStatus.DONE, Instant.parse("2026-07-15T00:00:00Z"));
        resumeRepository.saveAllAndFlush(List.of(failed, existing));
        Long failedId = failed.getId();
        entityManager.clear();

        Resume reloaded = resumeRepository.findById(failedId).orElseThrow();
        reloaded.replaceUpload("new/key.pdf", "newhash");
        resumeRepository.flush();
        entityManager.clear();

        Resume updated = resumeRepository.findById(failedId).orElseThrow();
        List<Resume> results = resumeRepository.findByUserIdOrderByLastUploadedAtDesc(USER_ID);

        assertThat(updated.getLastUploadedAt()).isAfter(failedLastUploadedAt);
        assertThat(updated.getParseStatus()).isEqualTo(ParseStatus.PROCESSING);
        assertThat(updated.getS3Key()).isEqualTo("new/key.pdf");
        assertThat(results).extracting(Resume::getId)
                .startsWith(failedId);
    }

    @Test
    @DisplayName("캐시 만료 시각 이하의 DONE Resume만 EXPIRED로 전환하고 정확한 처리 건수를 반환한다")
    void expireResumes_expiresDoneResumeOnlyAndReturnsAffectedCount() {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        Resume expired = doneResume("expired text", now);
        Resume notExpired = doneResume("active text", now.plusSeconds(1));
        resumeRepository.saveAllAndFlush(List.of(expired, notExpired));
        Long expiredId = expired.getId();
        Long notExpiredId = notExpired.getId();

        int expiredCount = resumeRepository.expireResumes(now, ParseStatus.DONE, ParseStatus.EXPIRED);
        entityManager.clear();

        assertThat(expiredCount).isEqualTo(1);
        Resume expiredResult = resumeRepository.findById(expiredId).orElseThrow();
        assertThat(expiredResult.getParseStatus()).isEqualTo(ParseStatus.EXPIRED);
        assertThat(expiredResult.getExtractedText()).isNull();
        assertThat(resumeRepository.findById(notExpiredId).orElseThrow().getExtractedText())
                .isEqualTo("active text");

        int notExpiredCount = resumeRepository.expireResumes(now, ParseStatus.DONE, ParseStatus.EXPIRED);

        assertThat(notExpiredCount).isZero();
    }

    @Test
    @DisplayName("업로드 슬롯 계산에서 FAILED와 EXPIRED를 모두 제외한다")
    void countByUserIdAndTypeAndParseStatusNotIn_excludesFailedAndExpired() {
        Resume processing = resume(USER_ID, ParseStatus.PROCESSING, Instant.now());
        Resume done = resume(USER_ID, ParseStatus.DONE, Instant.now());
        Resume failed = resume(USER_ID, ParseStatus.FAILED, Instant.now());
        Resume expired = resume(USER_ID, ParseStatus.EXPIRED, Instant.now());
        resumeRepository.saveAllAndFlush(List.of(processing, done, failed, expired));

        long occupiedSlots = resumeRepository.countByUserIdAndTypeAndParseStatusNotIn(
                USER_ID,
                ResumeType.RESUME,
                Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED));

        assertThat(occupiedSlots).isEqualTo(2L);
    }

    private Resume resume(Long userId, ParseStatus status, Instant lastUploadedAt) {
        Resume resume = new Resume(userId, ResumeType.RESUME, "some/key.pdf", "somehash");
        ReflectionTestUtils.setField(resume, "parseStatus", status);
        ReflectionTestUtils.setField(resume, "lastUploadedAt", lastUploadedAt);
        return resume;
    }

    private Resume doneResume(String text, Instant cacheExpiresAt) {
        Resume resume = new Resume(USER_ID, ResumeType.RESUME, "some/key.pdf", "somehash");
        resume.markDone(text, cacheExpiresAt);
        return resume;
    }
}
