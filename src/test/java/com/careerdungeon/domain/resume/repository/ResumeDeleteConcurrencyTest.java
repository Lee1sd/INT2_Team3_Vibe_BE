package com.careerdungeon.domain.resume.repository;

import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ResumeDeleteConcurrencyTest {

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentSoftDelete_allowsExactlyOneRequestToSucceed() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long resumeId = transaction.execute(status ->
                resumeRepository.saveAndFlush(
                        new Resume(991L, ResumeType.RESUME, "concurrent/key.pdf", "hash")).getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> deleteAtSameTime(transaction, resumeId, ready, start));
            Future<Integer> second = executor.submit(() -> deleteAtSameTime(transaction, resumeId, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(1, 0);

            Resume deleted = resumeRepository.findById(resumeId).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(deleted.getS3Key()).isNull();
            assertThat(deleted.getFileHash()).isNull();
            assertThat(deleted.getExtractedText()).isNull();
        } finally {
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> resumeRepository.deleteById(resumeId));
        }
    }

    private int deleteAtSameTime(TransactionTemplate transaction,
                                 Long resumeId,
                                 CountDownLatch ready,
                                 CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return transaction.execute(status -> resumeRepository.softDeleteIfActive(
                resumeId, 991L, Instant.now()));
    }
}
