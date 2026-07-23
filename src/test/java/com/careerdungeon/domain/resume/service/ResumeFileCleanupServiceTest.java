package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.FileCleanupStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeFileCleanupTask;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeObjectVersionMismatchException;
import com.careerdungeon.domain.resume.repository.ResumeFileCleanupTaskRepository;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
class ResumeFileCleanupServiceTest {

    @Autowired
    private ResumeFileCleanupTaskRepository cleanupTaskRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Test
    void failedTask_isRetriedAndDeletedOnlyAfterFileDeletionSucceeds() throws Exception {
        ResumeFileStorage storage = mock(ResumeFileStorage.class);
        ResumeFileCleanupService service = new ResumeFileCleanupService(cleanupTaskRepository, storage);
        doThrow(new RuntimeException("temporary failure")).doNothing()
                .when(storage).delete("retry/key.pdf", "retry-etag");
        service.enqueue(501L, "retry/key.pdf", "retry-etag");

        service.retryPendingTasks();

        ResumeFileCleanupTask failed = cleanupTaskRepository.findAll().get(0);
        assertThat(failed.getStatus()).isEqualTo(FileCleanupStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastAttemptAt()).isNotNull();
        assertThat(failed.getS3Etag()).isEqualTo("retry-etag");

        service.retryPendingTasks();

        assertThat(cleanupTaskRepository.findAll()).isEmpty();
    }

    @Test
    void cleanupTask_survivesResumeDeletionBecauseItHasNoResumeForeignKey() {
        Resume resume = resumeRepository.saveAndFlush(
                new Resume(1L, ResumeType.RESUME, "withdrawal/key.pdf", "hash"));
        ResumeFileStorage storage = mock(ResumeFileStorage.class);
        ResumeFileCleanupService service = new ResumeFileCleanupService(cleanupTaskRepository, storage);
        service.enqueue(resume.getId(), "withdrawal/key.pdf");

        resumeRepository.deleteById(resume.getId());
        resumeRepository.flush();

        assertThat(cleanupTaskRepository.findAll())
                .singleElement()
                .extracting(ResumeFileCleanupTask::getS3Key)
                .isEqualTo("withdrawal/key.pdf");
    }

    @Test
    void versionMismatchCompletesTaskWithoutDeletingReplacementObject() {
        ResumeFileStorage storage = mock(ResumeFileStorage.class);
        ResumeFileCleanupService service = new ResumeFileCleanupService(cleanupTaskRepository, storage);
        doThrow(new ResumeObjectVersionMismatchException(new RuntimeException("etag mismatch")))
                .when(storage).delete("replaced/key.pdf", "verified-etag");
        service.enqueue(501L, "replaced/key.pdf", "verified-etag");

        service.retryPendingTasks();

        assertThat(cleanupTaskRepository.findAll()).isEmpty();
    }
}
