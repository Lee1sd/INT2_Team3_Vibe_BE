package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.exception.ResumeFileTypeNotAllowedException;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ResumeService sut;

    // upload_success에서 실제로 생성된 로컬 임시 파일 — 테스트 후 정리한다.
    private Path createdTempFile;

    @BeforeEach
    void setUp() {
        sut = new ResumeService(resumeRepository, eventPublisher);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (createdTempFile != null) {
            Files.deleteIfExists(createdTempFile);
        }
    }

    @Test
    @DisplayName("upload(): 정상 업로드 시 SHA-256 해시 계산, 로컬 임시 파일 생성, 이벤트 발행, PROCESSING 응답")
    void upload_success() throws Exception {
        given(resumeRepository.countByUserIdAndTypeAndParseStatusNot(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(0L);
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatus(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(Optional.empty());
        given(resumeRepository.save(any(Resume.class))).willAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            ReflectionTestUtils.setField(resume, "id", 501L);
            return resume;
        });

        byte[] content = "dummy-pdf-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", content);

        ResumeResponse response = sut.upload(1L, ResumeType.RESUME, file);

        assertThat(response.resumeId()).isEqualTo(501L);
        assertThat(response.type()).isEqualTo(ResumeType.RESUME);
        assertThat(response.parseStatus()).isEqualTo(ParseStatus.PROCESSING);
        assertThat(response.extractedText()).isNull();

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Resume saved = captor.getValue();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expectedHash = HexFormat.of().formatHex(digest.digest(content));
        assertThat(saved.getFileHash()).isEqualTo(expectedHash);

        createdTempFile = Path.of(saved.getS3Key());
        assertThat(Files.exists(createdTempFile)).isTrue();
        assertThat(Files.readAllBytes(createdTempFile)).isEqualTo(content);

        verify(eventPublisher).publishEvent(new ResumeUploadedEvent(501L));
    }

    @Test
    @DisplayName("upload(): DB 저장 실패 시 이미 생성한 로컬 임시 파일을 삭제한다")
    void upload_databaseSaveFails_deletesTempFile() {
        given(resumeRepository.countByUserIdAndTypeAndParseStatusNot(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(0L);
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatus(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(Optional.empty());
        given(resumeRepository.save(any(Resume.class)))
                .willThrow(new RuntimeException("DB save failed"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> sut.upload(1L, ResumeType.RESUME, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB save failed");

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Path tempFile = Path.of(captor.getValue().getS3Key());
        assertThat(Files.exists(tempFile)).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("upload(): 메서드 반환 후 트랜잭션 롤백 시 로컬 임시 파일을 삭제한다")
    void upload_transactionRollsBackAfterReturn_deletesTempFile() {
        TransactionSynchronizationManager.initSynchronization();
        given(resumeRepository.countByUserIdAndTypeAndParseStatusNot(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(0L);
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatus(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(Optional.empty());
        given(resumeRepository.save(any(Resume.class))).willAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            ReflectionTestUtils.setField(resume, "id", 501L);
            return resume;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "content".getBytes());

        sut.upload(1L, ResumeType.RESUME, file);

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Path tempFile = Path.of(captor.getValue().getS3Key());
        assertThat(Files.exists(tempFile)).isTrue();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    @DisplayName("upload(): 유효한(FAILED 아닌) 업로드가 3개 초과 시 ResumeTypeLimitExceededException, save()/이벤트 발행 안 함")
    void upload_typeLimitExceeded_throwsException() {
        given(resumeRepository.countByUserIdAndTypeAndParseStatusNot(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(3L);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> sut.upload(1L, ResumeType.RESUME, file))
                .isInstanceOf(ResumeTypeLimitExceededException.class);

        verify(resumeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("upload(): FAILED 슬롯이 있으면 새로 insert하지 않고 replaceUpload()로 그 슬롯을 재사용한다")
    void upload_reusesFailedSlot() throws Exception {
        Resume failedResume = new Resume(1L, ResumeType.RESUME, "old/path", "oldhash");
        ReflectionTestUtils.setField(failedResume, "id", 777L);
        Instant previousCreatedAt = Instant.parse("2026-07-01T00:00:00Z");
        ReflectionTestUtils.setField(failedResume, "createdAt", previousCreatedAt);
        failedResume.markFailed();

        given(resumeRepository.countByUserIdAndTypeAndParseStatusNot(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(0L);
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatus(1L, ResumeType.RESUME, ParseStatus.FAILED))
                .willReturn(Optional.of(failedResume));

        byte[] content = "retry-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", content);

        ResumeResponse response = sut.upload(1L, ResumeType.RESUME, file);

        assertThat(response.resumeId()).isEqualTo(777L);
        assertThat(response.parseStatus()).isEqualTo(ParseStatus.PROCESSING);
        assertThat(failedResume.getParseStatus()).isEqualTo(ParseStatus.PROCESSING);
        assertThat(failedResume.getCreatedAt()).isAfter(previousCreatedAt);

        createdTempFile = Path.of(failedResume.getS3Key());
        assertThat(Files.exists(createdTempFile)).isTrue();
        assertThat(Files.readAllBytes(createdTempFile)).isEqualTo(content);

        // 관리 대상 엔티티를 재사용하는 경로이므로 새 save() 호출은 없어야 한다.
        verify(resumeRepository, never()).save(any());
    }

    @Test
    @DisplayName("upload(): 허용되지 않은 확장자(.exe)면 ResumeFileTypeNotAllowedException, repository 호출 안 함")
    void upload_disallowedExtension_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> sut.upload(1L, ResumeType.RESUME, file))
                .isInstanceOf(ResumeFileTypeNotAllowedException.class);

        verify(resumeRepository, never()).countByUserIdAndTypeAndParseStatusNot(any(), any(), any());
        verify(resumeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("getStatus(): 정상 조회 시 ResumeResponse 반환")
    void getStatus_success() {
        Resume resume = new Resume(1L, ResumeType.RESUME, "some/s3/key", "somehash");
        ReflectionTestUtils.setField(resume, "id", 501L);
        resume.markDone("추출된 텍스트", Instant.now().plusSeconds(3600));

        given(resumeRepository.findByIdAndUserId(501L, 1L)).willReturn(Optional.of(resume));

        ResumeResponse response = sut.getStatus(1L, 501L);

        assertThat(response.resumeId()).isEqualTo(501L);
        assertThat(response.type()).isEqualTo(ResumeType.RESUME);
        assertThat(response.parseStatus()).isEqualTo(ParseStatus.DONE);
        assertThat(response.extractedText()).isEqualTo("추출된 텍스트");
    }

    @Test
    @DisplayName("getStatus(): 존재하지 않는 resumeId 조회 시 ResumeNotFoundException")
    void getStatus_notFound_throwsException() {
        given(resumeRepository.findByIdAndUserId(999L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getStatus(1L, 999L))
                .isInstanceOf(ResumeNotFoundException.class);
    }

    @Test
    @DisplayName("getResumes(): 상태를 필터링하지 않고 repository의 최신순 결과를 요약 DTO로 반환")
    void getResumes_returnsAllStatusesInCreatedAtDescendingOrder() {
        Resume failed = resume(503L, ParseStatus.FAILED, Instant.parse("2026-07-16T03:00:00Z"));
        Resume done = resume(502L, ParseStatus.DONE, Instant.parse("2026-07-16T02:00:00Z"));
        Resume processing = resume(501L, ParseStatus.PROCESSING, Instant.parse("2026-07-16T01:00:00Z"));
        given(resumeRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(failed, done, processing));

        List<ResumeSummaryResponse> responses = sut.getResumes(1L);

        assertThat(responses).extracting(ResumeSummaryResponse::resumeId)
                .containsExactly(503L, 502L, 501L);
        assertThat(responses).extracting(ResumeSummaryResponse::parseStatus)
                .containsExactly(ParseStatus.FAILED, ParseStatus.DONE, ParseStatus.PROCESSING);
        assertThat(responses).extracting(ResumeSummaryResponse::createdAt)
                .containsExactly(
                        Instant.parse("2026-07-16T03:00:00Z"),
                        Instant.parse("2026-07-16T02:00:00Z"),
                        Instant.parse("2026-07-16T01:00:00Z"));
        verify(resumeRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    private Resume resume(Long id, ParseStatus status, Instant createdAt) {
        Resume resume = new Resume(1L, ResumeType.RESUME, "some/s3/key", "somehash");
        ReflectionTestUtils.setField(resume, "id", id);
        ReflectionTestUtils.setField(resume, "parseStatus", status);
        ReflectionTestUtils.setField(resume, "createdAt", createdAt);
        return resume;
    }
}
