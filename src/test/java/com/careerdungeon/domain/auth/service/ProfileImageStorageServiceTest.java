package com.careerdungeon.domain.auth.service;

import com.careerdungeon.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이슈 #98/ADR-018 — 프로필 이미지 업로드 검증, 교체/탈퇴 시 사용하는 삭제의
 * best-effort(예외를 던지지 않음) 동작, presigned URL 생성을 S3Client/S3Presigner를
 * Mockito로 모킹해서 검증한다. 실제 AWS 자격증명이나 네트워크 호출은 필요 없다.
 */
@ExtendWith(MockitoExtension.class)
class ProfileImageStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private static final String BUCKET = "test-bucket";

    private ProfileImageStorageService service() {
        return new ProfileImageStorageService(s3Client, s3Presigner, BUCKET);
    }

    @Test
    void upload_withValidJpeg_putsObjectUnderUserPrefixWithJpgExtension() {
        MultipartFile file = new MockMultipartFile("photo", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String key = service().upload(42L, file);

        assertThat(key).startsWith("profile-images/42/").endsWith(".jpg");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(key);
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void upload_withEmptyFile_throwsBadRequest() {
        MultipartFile file = new MockMultipartFile("photo", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service().upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("업로드할 이미지가 없습니다");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void upload_withUnsupportedMimeType_throwsBadRequest() {
        MultipartFile file = new MockMultipartFile("photo", "a.gif", "image/gif", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service().upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("지원하지 않는 이미지 형식");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void upload_withOversizedFile_throwsPayloadTooLarge() {
        byte[] tooLarge = new byte[2 * 1024 * 1024 + 1];
        MultipartFile file = new MockMultipartFile("photo", "big.png", "image/png", tooLarge);

        assertThatThrownBy(() -> service().upload(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2MB");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void delete_withNullKey_doesNothing() {
        assertThatCode(() -> service().delete(null)).doesNotThrowAnyException();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_withValidKey_callsDeleteObject() {
        service().delete("profile-images/1/old.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("profile-images/1/old.jpg");
    }

    @Test
    void delete_whenS3ThrowsException_swallowsAndDoesNotPropagate() {
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(SdkException.create("boom", null));

        assertThatCode(() -> service().delete("profile-images/1/old.jpg")).doesNotThrowAnyException();
    }

    @Test
    void presignedUrl_withNullKey_returnsNull() {
        assertThat(service().presignedUrl(null)).isNull();
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void presignedUrl_withKey_returnsGeneratedUrl() throws Exception {
        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(URI.create("https://bucket.s3.amazonaws.com/profile-images/1/a.jpg?X-Amz-Signature=abc").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presigned);

        String url = service().presignedUrl("profile-images/1/a.jpg");

        assertThat(url).isEqualTo("https://bucket.s3.amazonaws.com/profile-images/1/a.jpg?X-Amz-Signature=abc");
    }
}
