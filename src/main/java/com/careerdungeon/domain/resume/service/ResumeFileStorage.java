package com.careerdungeon.domain.resume.service;

/**
 * 이력서 원본 저장소의 공통 계약이다.
 * 운영 S3와 로컬 임시파일 구현이 서비스·파싱·정리 로직에 동일한 방식으로 연결되게 한다.
 */
public interface ResumeFileStorage {

    /** 사용자별 pending key와 제한 시간 업로드 URL을 발급한다. */
    PresignedResumeUpload createPresignedUpload(Long userId, String extension,
                                                long contentLength, String contentType);

    /** 저장된 원본의 실제 크기와 객체 버전 식별값을 조회한다. */
    StoredResumeFileMetadata metadata(String key);

    /** 기대한 객체 버전과 같을 때만 원본 바이트를 반환한다. */
    byte[] download(String key, String eTag);

    /** 객체 버전 조건 없이 원본 바이트를 반환하는 편의 메서드다. */
    default byte[] download(String key) {
        return download(key, null);
    }

    /** 기대한 객체 버전과 같을 때만 원본을 삭제한다. */
    void delete(String key, String eTag);

    /** 객체 버전 조건 없이 원본을 삭제하는 편의 메서드다. */
    default void delete(String key) {
        delete(key, null);
    }
}
