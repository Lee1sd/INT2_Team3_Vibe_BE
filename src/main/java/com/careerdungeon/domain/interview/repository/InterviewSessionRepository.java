package com.careerdungeon.domain.interview.repository;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.InterviewSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id")
    Optional<InterviewSession> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select new com.careerdungeon.domain.interview.repository.InterviewHistoryRow(
                pc.level,
                s.id,
                s.createdAt,
                jr.totalScore
            )
            from InterviewSession s
            join s.personaConfig pc
            join JudgmentResult jr on jr.session = s
            where s.user.id = :userId
              and s.status = :completedStatus
              and exists (
                  select m.id
                  from Message m
                  where m.session = s
              )
            order by pc.level asc, s.createdAt desc, s.id desc
            """)
    List<InterviewHistoryRow> findCompletedHistoryByUserId(
            @Param("userId") Long userId,
            @Param("completedStatus") InterviewSessionStatus completedStatus);

    default List<InterviewHistoryRow> findCompletedHistoryByUserId(Long userId) {
        return findCompletedHistoryByUserId(userId, InterviewSessionStatus.COMPLETED);
    }
}
