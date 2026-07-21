package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeFileCleanupTask;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeFileCleanupTaskRepository;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ResumeFileCleanupServiceTest {

    @Autowired
    private ResumeFileCleanupTaskRepository cleanupTaskRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @TempDir
    Path tempDir;

    @Test
    void cleanupFailure_preservesS3KeyInRetryTask() throws Exception {
        Resume resume = resumeRepository.saveAndFlush(
                new Resume(1L, ResumeType.RESUME, "original/key.pdf", "hash"));
        Path nonEmptyDirectory = Files.createDirectory(tempDir.resolve("not-a-file"));
        Files.writeString(nonEmptyDirectory.resolve("child"), "keep directory non-empty");
        ResumeFileCleanupService service = new ResumeFileCleanupService(cleanupTaskRepository);

        service.cleanup(resume.getId(), nonEmptyDirectory.toString());

        assertThat(cleanupTaskRepository.findAll())
                .singleElement()
                .satisfies(task -> assertTask(task, resume.getId(), nonEmptyDirectory.toString()));
    }

    private void assertTask(ResumeFileCleanupTask task, Long resumeId, String s3Key) {
        assertThat(task.getResumeId()).isEqualTo(resumeId);
        assertThat(task.getS3Key()).isEqualTo(s3Key);
        assertThat(task.getCreatedAt()).isNotNull();
    }
}
