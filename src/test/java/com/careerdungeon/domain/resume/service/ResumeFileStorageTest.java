package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeFileStorageTest {
    private static final String BUCKET = "test-resume-bucket";

    @Mock S3Client s3Client;
    @Mock S3Presigner presigner;
    @Mock PresignedPutObjectRequest presignedRequest;
    private ResumeFileStorage sut;

    @BeforeEach
    void setUp() {
        sut = new ResumeFileStorage(s3Client, presigner, BUCKET, 300L);
    }

    @Test
    void createsPresignedPutWithExpectedBucketKeyHeadersAndExpiration() throws Exception {
        given(presigner.presignPutObject(org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class)))
                .willReturn(presignedRequest);
        given(presignedRequest.url()).willReturn(new URL("https://example.com/upload"));

        PresignedResumeUpload result = sut.createPresignedUpload(
                7L, "pdf", 1234L, "application/pdf");

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(captor.capture());
        PutObjectPresignRequest request = captor.getValue();

        assertThat(request.signatureDuration()).isEqualTo(Duration.ofSeconds(300));
        assertThat(request.putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(request.putObjectRequest().key())
                .startsWith("resumes/7/pending/")
                .endsWith(".pdf");
        assertThat(request.putObjectRequest().contentLength()).isEqualTo(1234L);
        assertThat(request.putObjectRequest().contentType()).isEqualTo("application/pdf");
        assertThat(result.uploadUrl()).isEqualTo("https://example.com/upload");
        assertThat(result.s3Key()).isEqualTo(request.putObjectRequest().key());
        assertThat(result.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void readsMetadataFromHeadObject() {
        given(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(42L).eTag("etag-1").build());

        StoredResumeFileMetadata result = sut.metadata("resumes/7/pending/id.txt");

        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("resumes/7/pending/id.txt");
        assertThat(result).isEqualTo(new StoredResumeFileMetadata(42L, "etag-1"));
    }

    @Test
    void downloadsObjectUsingHeadEtagAsIfMatch() {
        byte[] expected = "resume".getBytes();
        given(s3Client.getObjectAsBytes(org.mockito.ArgumentMatchers.any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected));

        byte[] result = sut.download("resumes/7/pending/id.txt", "etag-1");

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("resumes/7/pending/id.txt");
        assertThat(captor.getValue().ifMatch()).isEqualTo("etag-1");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deletesExpectedObject() {
        sut.delete("resumes/7/pending/id.pdf");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("resumes/7/pending/id.pdf");
    }

    @Test
    void mapsHeadObject404ToUploadNotFound() {
        given(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertThatThrownBy(() -> sut.metadata("resumes/7/pending/missing.pdf"))
                .isInstanceOf(ResumeUploadNotFoundException.class);
    }

    @Test
    void mapsClientOrCredentialFailureToStorageUnavailable() {
        given(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class)))
                .willThrow(SdkClientException.create("credentials unavailable"));

        assertThatThrownBy(() -> sut.metadata("resumes/7/pending/id.pdf"))
                .isInstanceOf(ResumeStorageException.class);
    }
}
