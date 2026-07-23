package com.careerdungeon.domain.resume.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResumeType type;

    @Column(name = "s3_key", length = 255)
    private String s3Key;

    @Column(name = "s3_etag", length = 255)
    private String s3Etag;

    @Lob
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    private ParseStatus parseStatus;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "cache_expires_at")
    private Instant cacheExpiresAt;

    @Column(name = "last_uploaded_at", nullable = false)
    private Instant lastUploadedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Resume() {
    }

    public Resume(Long userId, ResumeType type, String s3Key, String fileHash) {
        this(userId, type, s3Key, fileHash, null);
    }

    public Resume(Long userId, ResumeType type, String s3Key, String fileHash, String s3Etag) {
        this.userId = userId;
        this.type = type;
        this.s3Key = s3Key;
        this.fileHash = fileHash;
        this.s3Etag = s3Etag;
        this.parseStatus = ParseStatus.PROCESSING;
        this.lastUploadedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    /**
     * 업로드 구분({@link ResumeType#RESUME} / {@link ResumeType#PORTFOLIO})을 반환한다.
     */
    public ResumeType getType() {
        return type;
    }

    /**
     * 원본 파일이 저장된 S3 객체 키를 반환한다.
     */
    public String getS3Key() {
        return s3Key;
    }

    public String getS3Etag() {
        return s3Etag;
    }

    /**
     * 파일 형식별 추출기로 얻은 텍스트(PII 마스킹 적용됨)를 반환한다. 파싱이 끝나기 전({@code PROCESSING})에는 {@code null}이다.
     */
    public String getExtractedText() {
        return extractedText;
    }

    /**
     * 파싱 상태({@link ParseStatus})를 반환한다.
     */
    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    /**
     * 재업로드 시 동일 파일 여부 판단 등에 쓰이는 원본 파일 해시를 반환한다.
     */
    public String getFileHash() {
        return fileHash;
    }

    /**
     * 추출 텍스트 캐시 만료 시각을 반환한다. 아직 파싱이 완료되지 않았다면 {@code null}이다.
     */
    public Instant getCacheExpiresAt() {
        return cacheExpiresAt;
    }

    public Instant getLastUploadedAt() {
        return lastUploadedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void delete() {
        this.deletedAt = Instant.now();
        this.extractedText = null;
        this.fileHash = null;
        this.s3Key = null;
        this.s3Etag = null;
    }

    /**
     * PDF/TXT/MD 텍스트 추출 성공 시 호출한다. {@code parseStatus}를 {@code PROCESSING -> DONE}으로 전이시키고,
     * 추출된 텍스트와 캐시 만료 시각(업로드 후 30일, NFR-14)을 함께 반영한다.
     * {@code docs/state/invariants-and-state-machines.md} §1 기준 {@code DONE}이 된 이후에는
     * 이 메서드로도 되돌아가지 않는다 — 재파싱이 필요하면 {@link #replaceUpload}로 새 업로드 주기를 시작해야 한다.
     */
    public void markDone(String extractedText, Instant cacheExpiresAt) {
        this.extractedText = extractedText;
        this.parseStatus = ParseStatus.DONE;
        this.cacheExpiresAt = cacheExpiresAt;
    }

    /**
     * 파일 추출 실패(암호화·손상 PDF, 잘못된 UTF-8 TXT/MD 등) 시 호출한다. {@code parseStatus}를
     * {@code PROCESSING -> FAILED}로 전이시킨다. {@code FAILED} 상태에서는 자동 재시도를 하지 않으며
     * (FR-01 예외처리), 사용자가 수동으로 재업로드해야만 {@link #replaceUpload}를 통해 다시
     * {@code PROCESSING}으로 돌아갈 수 있다.
     */
    public void markFailed() {
        this.parseStatus = ParseStatus.FAILED;
    }

    /**
     * 동일 {@code type}에 대한 재업로드(UPSERT)를 처리한다. {@code s3Key}/{@code fileHash}/{@code s3Etag}를 새
     * 파일 정보로 교체하고, 이전 파싱 결과({@code extractedText}, {@code cacheExpiresAt})를 비운 뒤
     * {@code parseStatus}를 {@code PROCESSING}으로 되돌리고 업로드 시각을 현재 시각으로 갱신하여
     * 파싱 상태 전이를 처음부터 다시 시작시킨다.
     * {@code DONE}/{@code FAILED} 어느 상태에서 호출되든 항상 이 전이가 강제된다.
     */
    public void replaceUpload(String s3Key, String fileHash) {
        replaceUpload(s3Key, fileHash, null);
    }

    public void replaceUpload(String s3Key, String fileHash, String s3Etag) {
        this.s3Key = s3Key;
        this.fileHash = fileHash;
        this.s3Etag = s3Etag;
        this.extractedText = null;
        this.parseStatus = ParseStatus.PROCESSING;
        this.cacheExpiresAt = null;
        this.lastUploadedAt = Instant.now();
    }

}
