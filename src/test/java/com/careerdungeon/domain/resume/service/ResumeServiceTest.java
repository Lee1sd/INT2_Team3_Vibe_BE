package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeUploadCompleteRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlRequest;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeFileTypeNotAllowedException;
import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.exception.ResumeStorageException;
import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {
    @Mock ResumeRepository repository;
    @Mock ResumeCapacityPolicy capacityPolicy;
    @Mock ResumeFileStorage storage;
    @Mock ResumeUploadPersistenceService persistence;
    @Mock ResumeFileCleanupService cleanup;
    private ResumeService sut;

    @BeforeEach
    void setUp() {
        sut = new ResumeService(repository, capacityPolicy,
                new ResumeFileValidator(), storage, persistence, cleanup);
    }

    @Test
    void issuesPresignedUrlAfterExtensionSizeAndCapacityValidation() {
        given(storage.createPresignedUpload(1L, "pdf", 100L, "application/pdf"))
                .willReturn(new PresignedResumeUpload("https://upload", "resumes/1/pending/id.pdf", 300));

        var result = sut.issueUploadUrl(1L,
                new ResumeUploadUrlRequest(ResumeType.RESUME, "resume.PDF", 100L, "application/pdf"));

        assertThat(result.uploadUrl()).isEqualTo("https://upload");
        assertThat(result.s3Key()).isEqualTo("resumes/1/pending/id.pdf");
        verify(capacityPolicy).ensureAvailable(1L, ResumeType.RESUME);
    }

    @Test
    void completionUsesHeadThenConditionalGetThenPersists() {
        String key = "resumes/1/pending/id.txt";
        byte[] bytes = "hello@example.com".getBytes(StandardCharsets.UTF_8);
        given(storage.metadata(key)).willReturn(new StoredResumeFileMetadata(bytes.length, "etag"));
        given(storage.download(key, "etag")).willReturn(bytes);
        given(persistence.persist(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(ResumeType.RESUME),
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("etag"),
                org.mockito.ArgumentMatchers.eq("resume.txt"),
                org.mockito.ArgumentMatchers.eq((long) bytes.length)))
                .willReturn(ResumeResponse.uploaded(10L, ResumeType.RESUME, ParseStatus.PROCESSING));

        ResumeResponse result = sut.completeUpload(1L,
                new ResumeUploadCompleteRequest(ResumeType.RESUME, key, "resume.txt"));

        assertThat(result.resumeId()).isEqualTo(10L);
        InOrder order = inOrder(storage, persistence);
        order.verify(storage).metadata(key);
        order.verify(storage).download(key, "etag");
        order.verify(persistence).persist(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(ResumeType.RESUME),
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("etag"),
                org.mockito.ArgumentMatchers.eq("resume.txt"),
                org.mockito.ArgumentMatchers.eq((long) bytes.length));
    }

    @Test
    void invalidContentIsDeletedImmediately() {
        String key = "resumes/1/pending/id.pdf";
        byte[] bytes = "fake pdf".getBytes();
        given(storage.metadata(key)).willReturn(new StoredResumeFileMetadata(bytes.length, "etag"));
        given(storage.download(key, "etag")).willReturn(bytes);

        assertThatThrownBy(() -> sut.completeUpload(1L,
                new ResumeUploadCompleteRequest(ResumeType.RESUME, key, "resume.pdf")))
                .isInstanceOf(ResumeParsingFailedException.class);

        verify(storage).delete(key, "etag");
    }

    @Test
    void completionRejectsOriginalFilenameWhoseExtensionDiffersFromIssuedKey() {
        String key = "resumes/1/pending/id.pdf";
        given(storage.metadata(key)).willReturn(new StoredResumeFileMetadata(1024L, "etag"));

        assertThatThrownBy(() -> sut.completeUpload(1L,
                new ResumeUploadCompleteRequest(ResumeType.RESUME, key, "resume.txt")))
                .isInstanceOf(ResumeFileTypeNotAllowedException.class);

        verify(storage).delete(key, "etag");
        verify(storage, never()).download(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedImmediateDeleteEnqueuesCleanupWithoutResumeId() {
        String key = "resumes/1/pending/id.pdf";
        byte[] bytes = "fake pdf".getBytes();
        given(storage.metadata(key)).willReturn(new StoredResumeFileMetadata(bytes.length, "etag"));
        given(storage.download(key, "etag")).willReturn(bytes);
        org.mockito.BDDMockito.willThrow(new RuntimeException("delete failed"))
                .given(storage).delete(key, "etag");

        assertThatThrownBy(() -> sut.completeUpload(1L,
                new ResumeUploadCompleteRequest(ResumeType.RESUME, key, "resume.pdf")))
                .isInstanceOf(ResumeParsingFailedException.class);

        verify(cleanup).enqueue(null, key, "etag");
    }

    @Test
    void rejectsAnotherUsersKeyBeforeCallingS3() {
        assertThatThrownBy(() -> sut.completeUpload(1L,
                new ResumeUploadCompleteRequest(
                        ResumeType.RESUME, "resumes/2/pending/id.pdf", "resume.pdf")))
                .isInstanceOf(ResumeUploadNotFoundException.class);
        verify(storage, never()).metadata(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void transientHeadFailureDoesNotDeletePotentiallyValidObject() {
        String key = "resumes/1/pending/id.pdf";
        given(storage.metadata(key)).willThrow(new ResumeStorageException("temporary"));

        assertThatThrownBy(() -> sut.completeUpload(1L,
                new ResumeUploadCompleteRequest(ResumeType.RESUME, key, "resume.pdf")))
                .isInstanceOf(ResumeStorageException.class);
        verify(storage, never()).delete(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<String>any());
        verify(cleanup, never()).enqueue(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<String>any());
    }

    @Test
    void getStatusUsesActiveOwnedResumeQuery() {
        Resume resume = resume(501L, ParseStatus.DONE);
        given(repository.findActiveByIdAndUserId(501L, 1L)).willReturn(Optional.of(resume));

        assertThat(sut.getStatus(1L, 501L).resumeId()).isEqualTo(501L);
        verify(repository).findActiveByIdAndUserId(501L, 1L);
    }

    @Test
    void listIncludesOriginalFilenameAndVerifiedFileSize() {
        Resume resume = new Resume(
                1L, ResumeType.RESUME, "resumes/1/pending/id.pdf", "hash", "etag",
                "backend-resume.pdf", 4096L);
        ReflectionTestUtils.setField(resume, "id", 501L);
        given(repository.findByUserIdOrderByLastUploadedAtDesc(1L)).willReturn(List.of(resume));

        var result = sut.getResumes(1L);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.originalFileName()).isEqualTo("backend-resume.pdf");
            assertThat(summary.fileSize()).isEqualTo(4096L);
        });
    }

    @Test
    void otherOwnersResumeIsReportedAsNotFound() {
        given(repository.findActiveByIdAndUserId(501L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getStatus(1L, 501L))
                .isInstanceOf(ResumeNotFoundException.class);
    }

    @Test
    void deleteSoftDeletesAndEnqueuesOriginalObject() {
        Resume resume = resume(501L, ParseStatus.DONE);
        given(repository.findActiveByIdAndUserId(501L, 1L)).willReturn(Optional.of(resume));
        given(repository.softDeleteIfActive(org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(Instant.class))).willReturn(1);

        sut.delete(1L, 501L);

        verify(cleanup).enqueue(501L, "resumes/1/pending/id.txt", "verified-etag");
    }

    @Test
    void concurrentSecondDeleteDoesNotEnqueueCleanup() {
        Resume resume = resume(501L, ParseStatus.DONE);
        given(repository.findActiveByIdAndUserId(501L, 1L)).willReturn(Optional.of(resume));
        given(repository.softDeleteIfActive(org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(Instant.class))).willReturn(0);

        assertThatThrownBy(() -> sut.delete(1L, 501L))
                .isInstanceOf(ResumeNotFoundException.class);
        verify(cleanup, never()).enqueue(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<String>any());
    }

    private Resume resume(Long id, ParseStatus status) {
        Resume resume = new Resume(1L, ResumeType.RESUME,
                "resumes/1/pending/id.txt", "hash", "verified-etag");
        ReflectionTestUtils.setField(resume, "id", id);
        ReflectionTestUtils.setField(resume, "parseStatus", status);
        return resume;
    }
}
