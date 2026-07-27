package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeObjectVersionMismatchException;
import com.careerdungeon.domain.resume.exception.ResumeStorageException;
import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * 운영 환경에서 private S3 객체를 Presigned URL과 ETag 조건으로 관리한다.
 */
@Component
@ConditionalOnProperty(name = "resume.storage.mode", havingValue = "s3", matchIfMissing = true)
public class S3ResumeFileStorage implements ResumeFileStorage {
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final long expirationSeconds;

    /** S3 클라이언트와 Presigner 및 업로드 정책 설정을 주입받는다. */
    public S3ResumeFileStorage(S3Client s3Client,
                               S3Presigner presigner,
                               @Value("${aws.s3.bucket:}") String bucket,
                               @Value("${resume.s3.presigned-upload-expiration-seconds:300}")
                               long expirationSeconds) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.expirationSeconds = expirationSeconds;
    }

    /** 사용자별 S3 key를 만들고 제한 시간 Presigned PUT URL을 발급한다. */
    @Override
    public PresignedResumeUpload createPresignedUpload(Long userId, String extension,
                                                       long contentLength, String contentType) {
        try {
            String key = "resumes/%d/pending/%s.%s".formatted(userId, UUID.randomUUID(), extension);
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(requiredBucket()).key(key).contentLength(contentLength).contentType(contentType).build();
            String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(expirationSeconds))
                            .putObjectRequest(put).build())
                    .url().toString();
            return new PresignedResumeUpload(url, key, expirationSeconds);
        } catch (SdkException e) {
            throw new ResumeStorageException("Failed to create resume upload URL.", e);
        }
    }

    /** S3 HeadObject 결과를 완료 검증용 메타데이터로 변환한다. */
    @Override
    public StoredResumeFileMetadata metadata(String key) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(requiredBucket()).key(key).build());
            return new StoredResumeFileMetadata(response.contentLength(), response.eTag());
        } catch (NoSuchKeyException e) {
            throw new ResumeUploadNotFoundException();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ResumeUploadNotFoundException();
            }
            throw new ResumeStorageException("Failed to inspect uploaded resume.", e);
        } catch (SdkException e) {
            throw new ResumeStorageException("Failed to inspect uploaded resume.", e);
        }
    }

    /** If-Match 조건으로 검증된 S3 객체 버전만 다운로드한다. */
    @Override
    public byte[] download(String key, String eTag) {
        try {
            GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(requiredBucket()).key(key);
            if (eTag != null && !eTag.isBlank()) {
                request.ifMatch(eTag);
            }
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(request.build());
            return bytes.asByteArray();
        } catch (S3Exception e) {
            if (e.statusCode() == 412) {
                throw new ResumeObjectVersionMismatchException(e);
            }
            throw new ResumeStorageException("Failed to download resume.", e);
        } catch (SdkException e) {
            throw new ResumeStorageException("Failed to download resume.", e);
        }
    }

    /** If-Match 조건으로 검증된 S3 객체 버전만 삭제한다. */
    @Override
    public void delete(String key, String eTag) {
        try {
            DeleteObjectRequest.Builder request = DeleteObjectRequest.builder()
                    .bucket(requiredBucket()).key(key);
            if (eTag != null && !eTag.isBlank()) {
                request.ifMatch(eTag);
            }
            s3Client.deleteObject(request.build());
        } catch (S3Exception e) {
            if (e.statusCode() == 412) {
                throw new ResumeObjectVersionMismatchException(e);
            }
            throw new ResumeStorageException("Failed to delete resume object.", e);
        } catch (SdkException e) {
            throw new ResumeStorageException("Failed to delete resume object.", e);
        }
    }

    /** 버킷 누락을 외부 SDK 호출 전에 명확한 도메인 오류로 변환한다. */
    private String requiredBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new ResumeStorageException("AWS S3 bucket is not configured.");
        }
        return bucket;
    }
}
