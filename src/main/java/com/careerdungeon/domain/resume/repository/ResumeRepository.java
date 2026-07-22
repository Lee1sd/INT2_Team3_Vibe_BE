package com.careerdungeon.domain.resume.repository;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    // FAILED는 실질적으로 점유한 슬롯이 아니므로 개수 제한 판단에서 제외한다.
    long countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
            Long userId, ResumeType type, Collection<ParseStatus> excludedStatuses);
    Optional<Resume> findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
            Long userId, ResumeType type, ParseStatus parseStatus);
    Optional<Resume> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 삭제되지 않은(deletedAt == null) Resume만 조회한다.
     * 면접 생성 시, 삭제된 이력서로 새 면접을 시작하지 못하도록 interview 도메인(InterviewService)에서 이
     * 메서드를 활용하는 것을 권장한다. (이슈 #121 관련, PR 리뷰 포인트에서 interview 담당자에게 공유됨)
     */
    @Query("select r from Resume r where r.id = :id and r.userId = :userId and r.deletedAt is null")
    Optional<Resume> findActiveByIdAndUserId(@Param("id") Long resumeId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resume r where r.id = :id and r.userId = :userId and r.deletedAt is null")
    Optional<Resume> findActiveByIdAndUserIdForUpdate(@Param("id") Long resumeId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Resume r
               set r.deletedAt = :deletedAt,
                   r.extractedText = null,
                   r.fileHash = null,
                   r.s3Key = null
             where r.id = :resumeId
               and r.userId = :userId
               and r.deletedAt is null
            """)
    int softDeleteIfActive(@Param("resumeId") Long resumeId,
                           @Param("userId") Long userId,
                           @Param("deletedAt") Instant deletedAt);

    @Query("select r from Resume r where r.userId = :userId and r.deletedAt is null order by r.lastUploadedAt desc")
    List<Resume> findByUserIdOrderByLastUploadedAtDesc(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Resume r
               set r.extractedText = :extractedText,
                   r.parseStatus = :parseStatus,
                   r.cacheExpiresAt = :cacheExpiresAt
             where r.id = :resumeId
               and r.deletedAt is null
            """)
    int updateParseResultIfActive(@Param("resumeId") Long resumeId,
                                  @Param("extractedText") String extractedText,
                                  @Param("parseStatus") ParseStatus parseStatus,
                                  @Param("cacheExpiresAt") Instant cacheExpiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Resume r
               set r.extractedText = null,
                   r.parseStatus = :expiredStatus
             where r.cacheExpiresAt <= :now
               and r.parseStatus = :doneStatus
               and r.deletedAt is null
            """)
    int expireResumes(@Param("now") Instant now,
                      @Param("doneStatus") ParseStatus doneStatus,
                      @Param("expiredStatus") ParseStatus expiredStatus);
}
