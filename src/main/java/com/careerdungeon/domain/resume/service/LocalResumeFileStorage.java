package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeLocalUploadRejectedException;
import com.careerdungeon.domain.resume.exception.ResumeObjectVersionMismatchException;
import com.careerdungeon.domain.resume.exception.ResumeStorageException;
import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬 시연 환경에서 이력서 원본을 OS 임시 디렉터리에 저장한다.
 * 업로드 URL은 인증된 사용자만 사용할 수 있고, 토큰은 짧게 만료되며 한 번만 소비된다.
 */
@Component
@ConditionalOnProperty(name = "resume.storage.mode", havingValue = "local")
public class LocalResumeFileStorage implements ResumeFileStorage {
    private final Path rootDirectory;
    private final String uploadBaseUrl;
    private final long expirationSeconds;
    private final Clock clock;
    private final Map<String, LocalUploadTicket> tickets = new ConcurrentHashMap<>();

    /** 로컬 프로필 설정을 실제 경로·URL·시계 정책으로 변환한다. */
    @Autowired
    public LocalResumeFileStorage(
            @Value("${resume.storage.local.directory:}") String configuredDirectory,
            @Value("${resume.storage.local.upload-base-url:"
                    + "http://localhost:8080/api/resumes/local-upload}") String uploadBaseUrl,
            @Value("${resume.s3.presigned-upload-expiration-seconds:300}") long expirationSeconds) {
        this(resolveRootDirectory(configuredDirectory), uploadBaseUrl, expirationSeconds, Clock.systemUTC());
    }

    /** 테스트에서 임시 경로와 시간을 결정적으로 주입하기 위한 생성자다. */
    LocalResumeFileStorage(Path rootDirectory, String uploadBaseUrl,
                           long expirationSeconds, Clock clock) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        this.uploadBaseUrl = stripTrailingSlash(uploadBaseUrl);
        this.expirationSeconds = Math.max(1L, expirationSeconds);
        this.clock = clock;
    }

    /**
     * S3 Presigned URL과 동일한 응답 계약을 유지하면서 로컬 PUT용 1회성 토큰을 발급한다.
     */
    @Override
    public PresignedResumeUpload createPresignedUpload(Long userId, String extension,
                                                       long contentLength, String contentType) {
        removeExpiredTickets();
        String key = "resumes/%d/pending/%s.%s".formatted(userId, UUID.randomUUID(), extension);
        String token = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plusSeconds(expirationSeconds);
        tickets.put(token, new LocalUploadTicket(userId, key, contentLength, contentType, expiresAt));
        return new PresignedResumeUpload(uploadBaseUrl + "/" + token, key, expirationSeconds);
    }

    /**
     * 인증 사용자와 발급 정보를 검증한 뒤 토큰을 소비하고 새 임시파일을 생성한다.
     */
    public void upload(Long userId, String token, String contentType, byte[] bytes) {
        LocalUploadTicket ticket = tickets.remove(token);
        if (ticket == null || !ticket.expiresAt().isAfter(clock.instant())
                || !ticket.userId().equals(userId)) {
            throw new ResumeUploadNotFoundException();
        }
        if (ticket.contentLength() != bytes.length || !ticket.contentType().equals(contentType)) {
            throw new ResumeLocalUploadRejectedException();
        }

        Path target = resolvePath(ticket.key());
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new ResumeStorageException("Failed to store resume in local temporary storage.", e);
        }
    }

    /** 임시파일의 실제 크기와 SHA-256 버전 식별값을 조회한다. */
    @Override
    public StoredResumeFileMetadata metadata(String key) {
        Path target = resolveExistingPath(key);
        try {
            byte[] bytes = Files.readAllBytes(target);
            return new StoredResumeFileMetadata(bytes.length, calculateEtag(bytes));
        } catch (IOException e) {
            throw new ResumeStorageException("Failed to inspect uploaded resume.", e);
        }
    }

    /** SHA-256 버전 식별값이 일치하는 임시파일만 다운로드한다. */
    @Override
    public byte[] download(String key, String eTag) {
        Path target = resolveExistingPath(key);
        try {
            byte[] bytes = Files.readAllBytes(target);
            verifyEtag(bytes, eTag);
            return bytes;
        } catch (IOException e) {
            throw new ResumeStorageException("Failed to download resume.", e);
        }
    }

    /** SHA-256 버전 식별값이 일치하는 임시파일만 삭제한다. */
    @Override
    public void delete(String key, String eTag) {
        Path target = resolvePath(key);
        if (!Files.isRegularFile(target)) {
            return;
        }
        try {
            verifyEtag(Files.readAllBytes(target), eTag);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new ResumeStorageException("Failed to delete resume object.", e);
        }
    }

    /**
     * 설정된 임시 루트 밖으로 벗어나는 경로는 저장소 장애로 처리해 파일 시스템 접근을 차단한다.
     */
    private Path resolvePath(String key) {
        if (key == null || key.isBlank() || key.contains("\\") || key.contains("..")) {
            throw new ResumeStorageException("Invalid local resume storage key.");
        }
        Path resolved = rootDirectory.resolve(key).normalize();
        if (!resolved.startsWith(rootDirectory)) {
            throw new ResumeStorageException("Invalid local resume storage key.");
        }
        return resolved;
    }

    /** 존재하는 정규 파일만 후속 검증 대상으로 반환한다. */
    private Path resolveExistingPath(String key) {
        Path resolved = resolvePath(key);
        if (!Files.isRegularFile(resolved)) {
            throw new ResumeUploadNotFoundException();
        }
        return resolved;
    }

    /** 기대한 SHA-256과 현재 바이트가 다르면 객체 버전 충돌로 변환한다. */
    private void verifyEtag(byte[] bytes, String expectedEtag) {
        if (expectedEtag != null && !expectedEtag.isBlank()
                && !calculateEtag(bytes).equals(expectedEtag)) {
            throw new ResumeObjectVersionMismatchException(
                    new IllegalStateException("Local resume ETag mismatch."));
        }
    }

    /** 로컬 파일의 객체 버전 식별값으로 사용할 SHA-256을 계산한다. */
    private String calculateEtag(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    /** 새 URL 발급 시 이미 만료된 미사용 토큰을 메모리에서 정리한다. */
    private void removeExpiredTickets() {
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    /** 별도 설정이 없으면 운영체제 임시 디렉터리 아래 안전한 기본 루트를 선택한다. */
    private static Path resolveRootDirectory(String configuredDirectory) {
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Path.of(configuredDirectory);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "career-dungeon", "resumes");
    }

    /** 토큰 경로를 붙일 때 이중 슬래시가 생기지 않도록 URL 끝 문자를 정리한다. */
    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Local resume upload base URL must not be blank.");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 발급 URL과 사용자·파일 제약을 연결하는 메모리 전용 1회성 정보다. */
    private record LocalUploadTicket(
            Long userId,
            String key,
            long contentLength,
            String contentType,
            Instant expiresAt
    ) {
    }
}
