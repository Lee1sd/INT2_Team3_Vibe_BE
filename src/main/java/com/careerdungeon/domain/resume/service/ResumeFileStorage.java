package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeStorageException;
import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
import org.springframework.beans.factory.annotation.Value;
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

@Component
public class ResumeFileStorage {
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final long expirationSeconds;

    public ResumeFileStorage(S3Client s3Client,
                             S3Presigner presigner,
                             @Value("${aws.s3.bucket:}") String bucket,
                             @Value("${resume.s3.presigned-upload-expiration-seconds:300}") long expirationSeconds) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.expirationSeconds = expirationSeconds;
    }

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

    public StoredResumeFileMetadata metadata(String key) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(requiredBucket()).key(key).build());
            return new StoredResumeFileMetadata(response.contentLength(), response.eTag());
        } catch (NoSuchKeyException e) {
            throw new ResumeUploadNotFoundException();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw new ResumeUploadNotFoundException();
            throw new ResumeStorageException("Failed to inspect uploaded resume.", e);
        } catch (SdkException e) {
            throw new ResumeStorageException("Failed to inspect uploaded resume.", e);
        }
    }

    public byte[] download(String key, String eTag) {
        try {
            GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(requiredBucket()).key(key);
            if (eTag != null && !eTag.isBlank()) request.ifMatch(eTag);
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(request.build());
            return bytes.asByteArray();
        } catch (S3Exception e) {
            throw new ResumeStorageException("Failed to download resume.", e);
        } catch (SdkException e) {
            throw new ResumeStorageException("Failed to download resume.", e);
        }
    }

    public byte[] download(String key) {
        return download(key, null);
    }

    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(requiredBucket()).key(key).build());
        } catch (S3Exception e) {
            throw new ResumeStorageException("Failed to delete resume object.", e);
        } catch (SdkException e) {
            throw new ResumeStorageException("Failed to delete resume object.", e);
        }
    }

    private String requiredBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new ResumeStorageException("AWS S3 bucket is not configured.");
        }
        return bucket;
    }
}
