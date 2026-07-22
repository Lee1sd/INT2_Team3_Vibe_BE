package com.careerdungeon.domain.progress.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/** 저장된 뱃지 키를 환경에 맞는 정적 경로 또는 private S3 임시 URL로 변환한다. */
@Service
public class BadgeImageUrlService {

    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(10);

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final boolean useS3;

    /** 공용 S3 Presigner, private 버킷과 환경별 이미지 전달 모드를 주입받는다. */
    public BadgeImageUrlService(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket:}") String bucket,
            @Value("${badge.images.use-s3:false}") boolean useS3) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.useS3 = useS3;
    }

    /** 개발 환경은 정적 경로를, 운영 환경은 10분 Presigned GET URL을 반환한다. */
    public String createImageUrl(String imageKey) {
        requireImageKey(imageKey);
        if (!useS3) {
            return imageKey.startsWith("/") ? imageKey : "/" + imageKey;
        }
        requireBucketConfigured();

        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_TTL)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(imageKey)
                        .build())
                .build();
        return s3Presigner.presignGetObject(request).url().toString();
    }

    /** object key가 빠진 상태로 정적 또는 S3 URL을 생성하는 것을 차단한다. */
    private void requireImageKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new IllegalArgumentException("뱃지 이미지 S3 키는 비어 있을 수 없습니다.");
        }
    }

    /** 운영 S3 모드에서만 private 버킷 설정을 필수로 검증한다. */
    private void requireBucketConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("aws.s3.bucket이 설정되지 않았습니다.");
        }
    }
}
