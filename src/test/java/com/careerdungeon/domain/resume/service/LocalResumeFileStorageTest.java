package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeLocalUploadRejectedException;
import com.careerdungeon.domain.resume.exception.ResumeObjectVersionMismatchException;
import com.careerdungeon.domain.resume.exception.ResumeStorageException;
import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalResumeFileStorageTest {
    private static final String UPLOAD_BASE_URL = "http://localhost:8080/api/resumes/local-upload";
    private static final byte[] PDF_BYTES = "%PDF-local-demo".getBytes();

    @TempDir
    Path tempDirectory;

    private MutableClock clock;
    private LocalResumeFileStorage sut;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        sut = new LocalResumeFileStorage(tempDirectory, UPLOAD_BASE_URL, 300L, clock);
    }

    @Test
    void storesDownloadsAndDeletesTemporaryResumeWithEtag() {
        PresignedResumeUpload issued = sut.createPresignedUpload(
                7L, "pdf", PDF_BYTES.length, "application/pdf");
        String token = issued.uploadUrl().substring(issued.uploadUrl().lastIndexOf('/') + 1);

        sut.upload(7L, token, "application/pdf", PDF_BYTES);

        StoredResumeFileMetadata metadata = sut.metadata(issued.s3Key());
        assertThat(metadata.contentLength()).isEqualTo(PDF_BYTES.length);
        assertThat(metadata.eTag()).isNotBlank();
        assertThat(sut.download(issued.s3Key(), metadata.eTag())).isEqualTo(PDF_BYTES);

        sut.delete(issued.s3Key(), metadata.eTag());
        assertThatThrownBy(() -> sut.metadata(issued.s3Key()))
                .isInstanceOf(ResumeUploadNotFoundException.class);
    }

    @Test
    void consumesUploadTokenOnlyOnce() {
        PresignedResumeUpload issued = sut.createPresignedUpload(
                7L, "pdf", PDF_BYTES.length, "application/pdf");
        String token = tokenOf(issued);

        sut.upload(7L, token, "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> sut.upload(7L, token, "application/pdf", PDF_BYTES))
                .isInstanceOf(ResumeUploadNotFoundException.class);
    }

    @Test
    void rejectsExpiredOrDifferentUserToken() {
        PresignedResumeUpload expired = sut.createPresignedUpload(
                7L, "pdf", PDF_BYTES.length, "application/pdf");
        clock.advanceSeconds(300);

        assertThatThrownBy(() -> sut.upload(7L, tokenOf(expired), "application/pdf", PDF_BYTES))
                .isInstanceOf(ResumeUploadNotFoundException.class);

        PresignedResumeUpload otherUser = sut.createPresignedUpload(
                7L, "pdf", PDF_BYTES.length, "application/pdf");
        assertThatThrownBy(() -> sut.upload(8L, tokenOf(otherUser), "application/pdf", PDF_BYTES))
                .isInstanceOf(ResumeUploadNotFoundException.class);
    }

    @Test
    void rejectsDifferentContentLengthOrContentType() {
        PresignedResumeUpload wrongLength = sut.createPresignedUpload(
                7L, "pdf", PDF_BYTES.length + 1L, "application/pdf");
        assertThatThrownBy(() -> sut.upload(
                7L, tokenOf(wrongLength), "application/pdf", PDF_BYTES))
                .isInstanceOf(ResumeLocalUploadRejectedException.class);

        PresignedResumeUpload wrongType = sut.createPresignedUpload(
                7L, "pdf", PDF_BYTES.length, "application/pdf");
        assertThatThrownBy(() -> sut.upload(
                7L, tokenOf(wrongType), "text/plain", PDF_BYTES))
                .isInstanceOf(ResumeLocalUploadRejectedException.class);
    }

    @Test
    void detectsChangedLocalObjectWithEtag() throws Exception {
        PresignedResumeUpload issued = sut.createPresignedUpload(
                7L, "pdf", PDF_BYTES.length, "application/pdf");
        sut.upload(7L, tokenOf(issued), "application/pdf", PDF_BYTES);
        String originalEtag = sut.metadata(issued.s3Key()).eTag();
        Files.write(tempDirectory.resolve(issued.s3Key()), "changed".getBytes());

        assertThatThrownBy(() -> sut.download(issued.s3Key(), originalEtag))
                .isInstanceOf(ResumeObjectVersionMismatchException.class);
        assertThatThrownBy(() -> sut.delete(issued.s3Key(), originalEtag))
                .isInstanceOf(ResumeObjectVersionMismatchException.class);
    }

    @Test
    void blocksPathTraversalOutsideTemporaryRoot() {
        assertThatThrownBy(() -> sut.metadata("../outside.pdf"))
                .isInstanceOf(ResumeStorageException.class);
        assertThatThrownBy(() -> sut.delete("resumes\\7\\pending\\outside.pdf", null))
                .isInstanceOf(ResumeStorageException.class);
    }

    private String tokenOf(PresignedResumeUpload upload) {
        return upload.uploadUrl().substring(upload.uploadUrl().lastIndexOf('/') + 1);
    }

    /**
     * 만료 경계를 실제 대기 없이 재현하기 위한 테스트 전용 시계다.
     */
    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
