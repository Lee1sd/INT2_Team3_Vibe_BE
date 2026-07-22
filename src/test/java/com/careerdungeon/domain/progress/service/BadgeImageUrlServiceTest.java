package com.careerdungeon.domain.progress.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** private S3 뱃지 object key의 Presigned GET URL 변환 계약을 검증한다. */
class BadgeImageUrlServiceTest {

    private final S3Presigner s3Presigner = mock(S3Presigner.class);

    /** 버킷·키·10분 TTL을 모두 포함해 브라우저용 URL을 생성하는지 검증한다. */
    @Test
    @DisplayName("뱃지 S3 키로 10분 Presigned GET URL을 생성한다")
    void createImageUrlUsesPrivateBucketContract() throws MalformedURLException {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(
                URI.create("https://int-team3.s3.amazonaws.com/badges/Level1.png?X-Amz-Signature=test")
                        .toURL());
        given(s3Presigner.presignGetObject(org.mockito.ArgumentMatchers.any(GetObjectPresignRequest.class)))
                .willReturn(presigned);
        BadgeImageUrlService service = new BadgeImageUrlService(
                s3Presigner,
                "int-team3-286688739992-ap-northeast-2-an",
                true);

        String result = service.createImageUrl("badges/Level1.png");

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(captor.getValue().getObjectRequest().bucket())
                .isEqualTo("int-team3-286688739992-ap-northeast-2-an");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("badges/Level1.png");
        assertThat(result).contains("X-Amz-Signature=test");
    }

    /** 버킷 미설정 상태에서는 서명 시도 전에 구성 오류를 반환하는지 검증한다. */
    @Test
    @DisplayName("S3 버킷이 없으면 Presigned URL을 생성하지 않는다")
    void createImageUrlRejectsMissingBucket() {
        BadgeImageUrlService service = new BadgeImageUrlService(s3Presigner, " ", true);

        assertThatIllegalStateException()
                .isThrownBy(() -> service.createImageUrl("badges/Level1.png"));
        verify(s3Presigner, never())
                .presignGetObject(org.mockito.ArgumentMatchers.any(GetObjectPresignRequest.class));
    }

    /** 비어 있는 object key가 S3 요청으로 전파되지 않는지 검증한다. */
    @Test
    @DisplayName("뱃지 S3 키가 비어 있으면 Presigned URL을 생성하지 않는다")
    void createImageUrlRejectsMissingImageKey() {
        BadgeImageUrlService service = new BadgeImageUrlService(s3Presigner, "test-bucket", true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createImageUrl(""));
        verify(s3Presigner, never())
                .presignGetObject(org.mockito.ArgumentMatchers.any(GetObjectPresignRequest.class));
    }

    /** 개발 모드에서는 AWS 버킷과 자격증명 없이 백엔드 정적 경로를 반환하는지 검증한다. */
    @Test
    @DisplayName("개발 모드에서는 뱃지 S3 키를 백엔드 정적 경로로 반환한다")
    void createImageUrlUsesStaticAssetInDevelopment() {
        BadgeImageUrlService service = new BadgeImageUrlService(s3Presigner, "", false);

        String result = service.createImageUrl("badges/Level1.png");

        assertThat(result).isEqualTo("/badges/Level1.png");
        verify(s3Presigner, never())
                .presignGetObject(org.mockito.ArgumentMatchers.any(GetObjectPresignRequest.class));
    }
}
