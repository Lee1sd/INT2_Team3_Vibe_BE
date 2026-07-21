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
    @DisplayName("삭제되지 않은 PROCESSING/DONE/FAILED/EXPIRED 이력서를 lastUploadedAt 내림차순으로 조회한다")
    void findByUserIdOrderByLastUploadedAtDesc_returnsAllStatusesForUserInDescendingOrder() {
        Resume processing = resume(USER_ID, ParseStatus.PROCESSING, Instant.parse("2026-07-16T01:00:00Z"));
        Resume done = resume(USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T02:00:00Z"));
        Resume failed = resume(USER_ID, ParseStatus.FAILED, Instant.parse("2026-07-16T03:00:00Z"));
        Resume expired = resume(USER_ID, ParseStatus.EXPIRED, Instant.parse("2026-07-16T04:00:00Z"));
        Resume deleted = resume(USER_ID, ParseStatus.FAILED, Instant.parse("2026-07-16T05:00:00Z"));
        Resume otherUser = resume(OTHER_USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T06:00:00Z"));
        deleted.delete();
        resumeRepository.saveAllAndFlush(List.of(processing, done, failed, expired, deleted, otherUser));
        entityManager.clear();

        List<Resume> results = resumeRepository.findByUserIdOrderByLastUploadedAtDesc(USER_ID);

        assertThat(results).extracting(Resume::getUserId)
                .containsOnly(USER_ID);
        assertThat(results).extracting(Resume::getParseStatus)
                .containsExactly(ParseStatus.EXPIRED, ParseStatus.FAILED, ParseStatus.DONE, ParseStatus.PROCESSING);
        assertThat(results).extracting(Resume::getId)
                .doesNotContain(deleted.getId());
        assertThat(results).extracting(Resume::getLastUploadedAt)
                .containsExactly(
                        Instant.parse("2026-07-16T04:00:00Z"),
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
    @DisplayName("소프트 삭제된 이력서는 사용자 목록과 유효 개수에서 제외한다")
    void softDeletedResume_isExcludedFromUserQueries() {
        Resume active = resume(USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T01:00:00Z"));
        Resume deleted = resume(USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T02:00:00Z"));
        deleted.delete();
        resumeRepository.saveAllAndFlush(List.of(active, deleted));
        entityManager.clear();

        List<Resume> results = resumeRepository
                .findByUserIdOrderByLastUploadedAtDesc(USER_ID);
        long validCount = resumeRepository.countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
                USER_ID, ResumeType.RESUME, Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED));

        assertThat(results).extracting(Resume::getId).containsExactly(active.getId());
        assertThat(validCount).isEqualTo(1L);
        assertThat(resumeRepository.findActiveByIdAndUserId(deleted.getId(), USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("findActiveByIdAndUserId는 타인 소유 Resume을 반환하지 않는다")
    void findActiveByIdAndUserId_otherOwner_returnsEmpty() {
        Resume otherUsersResume = resume(
                OTHER_USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T01:00:00Z"));
        resumeRepository.saveAndFlush(otherUsersResume);
        entityManager.clear();

        assertThat(resumeRepository.findActiveByIdAndUserId(otherUsersResume.getId(), USER_ID)).isEmpty();
        assertThat(resumeRepository.findActiveByIdAndUserId(otherUsersResume.getId(), OTHER_USER_ID)).isPresent();
    }

    @Test
    @DisplayName("조건부 소프트 삭제는 소유자에게 한 번만 성공하고 개인정보를 함께 파기한다")
    void softDeleteIfActive_allowsOwnerExactlyOnceAndPurgesPersonalData() {
        Resume resume = resume(USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T01:00:00Z"));
        resume.markDone("개인정보가 포함된 텍스트", Instant.parse("2026-08-15T00:00:00Z"));
        resumeRepository.saveAndFlush(resume);
        Long resumeId = resume.getId();
        entityManager.clear();

        int otherOwner = resumeRepository.softDeleteIfActive(resumeId, OTHER_USER_ID, Instant.now());
        int firstOwnerRequest = resumeRepository.softDeleteIfActive(resumeId, USER_ID, Instant.now());
        int secondOwnerRequest = resumeRepository.softDeleteIfActive(resumeId, USER_ID, Instant.now());
        entityManager.clear();

        Resume deleted = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(otherOwner).isZero();
        assertThat(firstOwnerRequest).isEqualTo(1);
        assertThat(secondOwnerRequest).isZero();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getExtractedText()).isNull();
        assertThat(deleted.getFileHash()).isNull();
        assertThat(deleted.getS3Key()).isNull();
    }

    @Test
    @DisplayName("삭제가 먼저 완료되면 늦게 끝난 파싱 결과는 저장되지 않고 개인정보는 DB에서 파기된 상태를 유지한다")
    void lateParseResultAfterDelete_doesNotRestorePersonalData() {
        Resume resume = resume(USER_ID, ParseStatus.DONE, Instant.parse("2026-07-16T01:00:00Z"));
        resume.markDone("기존 추출 텍스트", Instant.parse("2026-08-15T00:00:00Z"));
        resumeRepository.saveAndFlush(resume);
        Long resumeId = resume.getId();
        entityManager.clear();

        Resume deleting = resumeRepository.findById(resumeId).orElseThrow();
        deleting.delete();
        resumeRepository.flush();
        entityManager.clear();

        int updated = resumeRepository.updateParseResultIfActive(
                resumeId,
                "삭제보다 늦게 끝난 파싱 결과",
                ParseStatus.DONE,
                Instant.parse("2026-08-16T00:00:00Z"));
        entityManager.clear();

        Resume deleted = resumeRepository.findById(resumeId).orElseThrow();
        assertThat(updated).isZero();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getExtractedText()).isNull();
        assertThat(deleted.getFileHash()).isNull();
        assertThat(deleted.getS3Key()).isNull();
    }

    @Test
    @DisplayName("만료 시각 이하의 활성 DONE Resume만 EXPIRED로 전환한다")
    void expireResumes_expiresOnlyActiveDoneResume() {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        Resume expired = doneResume("expired text", now);
        Resume notExpired = doneResume("active text", now.plusSeconds(1));
        Resume deleted = doneResume("deleted text", now);
        deleted.delete();
        resumeRepository.saveAllAndFlush(List.of(expired, notExpired, deleted));
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
    }

    @Test
    @DisplayName("업로드 슬롯 계산에서 FAILED, EXPIRED, 삭제된 Resume을 제외한다")
    void occupiedSlotCount_excludesFailedExpiredAndDeleted() {
        Resume processing = resume(USER_ID, ParseStatus.PROCESSING, Instant.now());
        Resume done = resume(USER_ID, ParseStatus.DONE, Instant.now());
        Resume failed = resume(USER_ID, ParseStatus.FAILED, Instant.now());
        Resume expired = resume(USER_ID, ParseStatus.EXPIRED, Instant.now());
        Resume deleted = resume(USER_ID, ParseStatus.DONE, Instant.now());
        deleted.delete();
        resumeRepository.saveAllAndFlush(List.of(processing, done, failed, expired, deleted));

        long occupiedSlots = resumeRepository.countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
                USER_ID, ResumeType.RESUME, Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED));

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
