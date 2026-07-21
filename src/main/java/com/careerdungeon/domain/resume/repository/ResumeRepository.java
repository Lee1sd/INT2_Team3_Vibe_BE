package com.careerdungeon.domain.resume.repository;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    // FAILED는 실질적으로 점유한 슬롯이 아니므로 개수 제한 판단에서 제외한다.
    long countByUserIdAndTypeAndParseStatusNotAndDeletedAtIsNull(
            Long userId, ResumeType type, ParseStatus parseStatus);
    Optional<Resume> findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
            Long userId, ResumeType type, ParseStatus parseStatus);
    Optional<Resume> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resume r where r.id = :id and r.userId = :userId and r.deletedAt is null")
    Optional<Resume> findOwnedByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<Resume> findByUserIdAndDeletedAtIsNullOrderByLastUploadedAtDesc(Long userId);
}
