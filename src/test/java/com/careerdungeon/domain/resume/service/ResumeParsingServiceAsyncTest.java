package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.parser.ResumeTextExtractor;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import com.careerdungeon.global.config.AsyncConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(classes = {AsyncConfig.class, ResumeParsingService.class})
class ResumeParsingServiceAsyncTest {

    @Autowired
    private ResumeParsingService resumeParsingService;

    @MockitoBean
    private ResumeRepository resumeRepository;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    @Test
    @DisplayName("handleResumeUploaded(): 호출 스레드와 다른 스레드에서 파싱을 시작한다")
    void handleResumeUploaded_runsOnAsyncExecutor() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        String callerThreadName = Thread.currentThread().getName();

        given(resumeRepository.findByIdAndDeletedAtIsNull(anyLong())).willAnswer(invocation -> {
            workerThreadName.set(Thread.currentThread().getName());
            invoked.countDown();
            return Optional.of(mock(Resume.class));
        });

        resumeParsingService.handleResumeUploaded(new ResumeUploadedEvent(501L));

        assertThat(invoked.await(3, TimeUnit.SECONDS))
                .as("비동기 파싱 작업이 제한 시간 안에 시작되어야 한다")
                .isTrue();
        assertThat(workerThreadName.get())
                .isNotEqualTo(callerThreadName);
    }
}
