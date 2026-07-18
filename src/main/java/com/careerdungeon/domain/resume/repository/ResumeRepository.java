package com.careerdungeon.domain.resume.repository;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    // FAILED는 실질적으로 점유한 슬롯이 아니므로 개수 제한 판단에서 제외한다.
    long countByUserIdAndTypeAndParseStatusNot(Long userId, ResumeType type, ParseStatus parseStatus);
    Optional<Resume> findFirstByUserIdAndTypeAndParseStatus(Long userId, ResumeType type, ParseStatus parseStatus);
    Optional<Resume> findByIdAndUserId(Long id, Long userId);
    List<Resume> findByUserIdOrderByLastUploadedAtDesc(Long userId);
}
