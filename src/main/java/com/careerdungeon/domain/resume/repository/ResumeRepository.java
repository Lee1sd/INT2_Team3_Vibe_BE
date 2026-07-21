package com.careerdungeon.domain.resume.repository;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    // FAILED와 EXPIRED는 실질적으로 점유한 슬롯이 아니므로 개수 제한 판단에서 제외한다.
    long countByUserIdAndTypeAndParseStatusNotIn(
            Long userId, ResumeType type, Collection<ParseStatus> excludedStatuses);
    Optional<Resume> findFirstByUserIdAndTypeAndParseStatus(Long userId, ResumeType type, ParseStatus parseStatus);
    Optional<Resume> findByIdAndUserId(Long id, Long userId);
    List<Resume> findByUserIdOrderByLastUploadedAtDesc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Resume r
               set r.extractedText = null,
                   r.parseStatus = :expiredStatus
             where r.cacheExpiresAt <= :now
               and r.parseStatus = :doneStatus
            """)
    int expireResumes(@Param("now") Instant now,
                      @Param("doneStatus") ParseStatus doneStatus,
                      @Param("expiredStatus") ParseStatus expiredStatus);
}
