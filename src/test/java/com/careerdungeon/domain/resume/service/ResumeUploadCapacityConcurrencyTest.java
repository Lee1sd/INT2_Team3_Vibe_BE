package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
class ResumeUploadCapacityConcurrencyTest {

    @Autowired ResumeRepository resumeRepository;
    @Autowired UserRepository userRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentCompletionAtLastSlotAllowsOnlyOneUpload() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("resume-capacity-race", "capacity@example.com", "capacity-user"));
        Long userId = user.getId();
        resumeRepository.saveAllAndFlush(List.of(
                new Resume(userId, ResumeType.RESUME, "resumes/%d/pending/one.pdf".formatted(userId), "hash-1"),
                new Resume(userId, ResumeType.RESUME, "resumes/%d/pending/two.pdf".formatted(userId), "hash-2")));

        ResumeCapacityPolicy capacityPolicy = new ResumeCapacityPolicy(resumeRepository);
        ResumeUploadPersistenceService persistence = new ResumeUploadPersistenceService(
                resumeRepository, mock(ApplicationEventPublisher.class), capacityPolicy);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> completeAtSameTime(
                    transaction, persistence, userId, "three-a", ready, start));
            Future<Boolean> second = executor.submit(() -> completeAtSameTime(
                    transaction, persistence, userId, "three-b", ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
            assertThat(resumeRepository.countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
                    userId, ResumeType.RESUME, Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED)))
                    .isEqualTo(3L);
        } finally {
            executor.shutdownNow();
            userRepository.deleteById(userId);
        }
    }

    private boolean completeAtSameTime(TransactionTemplate transaction,
                                       ResumeUploadPersistenceService persistence,
                                       Long userId,
                                       String objectId,
                                       CountDownLatch ready,
                                       CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            transaction.executeWithoutResult(status -> persistence.persist(
                    userId, ResumeType.RESUME,
                    "resumes/%d/pending/%s.pdf".formatted(userId, objectId),
                    "hash-" + objectId, "etag-" + objectId,
                    objectId + ".pdf", "이력서.pdf", 1024L));
            return true;
        } catch (ResumeTypeLimitExceededException expected) {
            return false;
        }
    }
}
