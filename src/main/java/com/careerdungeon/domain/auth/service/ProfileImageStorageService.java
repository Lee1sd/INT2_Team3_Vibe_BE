package com.careerdungeon.domain.auth.service;

import com.careerdungeon.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 마이페이지 프로필 이미지의 S3 저장을 전담한다(ADR-020). 이력서(RS-001) 업로드
 * 파이프라인과는 보관 정책이 달라 재사용하지 않는다 — 이력서는 파싱 후 즉시 삭제,
 * 프로필 이미지는 계속 보관한다.
 */
@Service
public class ProfileImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ProfileImageStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "profile-images";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public ProfileImageStorageService(S3Client s3Client, S3Presigner s3Presigner,
                                       @Value("${aws.s3.bucket:}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    /**
     * 업로드 검증 후 S3에 새 객체로 저장하고 object key를 반환한다. 기존 이미지를
     * 덮어쓰지 않는다 — 매번 새 UUID 키를 쓴다(교체 시 이전 키 삭제는 호출자 책임).
     */
    public String upload(Long userId, MultipartFile file) {
        validate(file);
        requireBucketConfigured();

        String key = buildKey(userId, file.getContentType());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | SdkException e) {
            throw new BusinessException(
                    "PROFILE_IMAGE_UPLOAD_FAILED", "프로필 이미지 업로드에 실패했습니다.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return key;
    }

    /**
     * S3 객체를 best-effort로 삭제한다 — 실패해도 예외를 던지지 않고 로그만 남긴다.
     * 교체 시 이전 객체 정리와 회원 탈퇴 시 삭제 모두, 이 삭제 자체의 실패가 주된
     * 흐름(새 사진 저장 완료, 탈퇴 완료)을 막으면 안 되기 때문이다(ADR-020).
     */
    public void delete(String key) {
        if (key == null || key.isBlank() || bucket == null || bucket.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.error("프로필 이미지 S3 객체 삭제 실패 (key={})", key, e);
        }
    }

    /** key가 없으면 null을 반환한다(프로필 이미지를 업로드한 적 없는 사용자). */
    public String presignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        requireBucketConfigured();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String buildKey(Long userId, String contentType) {
        return KEY_PREFIX + "/" + userId + "/" + UUID.randomUUID() + extensionFor(contentType);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    "PROFILE_IMAGE_EMPTY", "업로드할 이미지가 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(
                    "PROFILE_IMAGE_UNSUPPORTED_TYPE",
                    "지원하지 않는 이미지 형식입니다. (jpeg, png, webp만 허용)",
                    HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(
                    "PROFILE_IMAGE_TOO_LARGE", "이미지 용량은 2MB를 초과할 수 없습니다.",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    private void requireBucketConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(
                    "aws.s3.bucket이 설정되지 않았습니다 — application-local.yml에 버킷 이름을 추가하세요.");
        }
    }
}
