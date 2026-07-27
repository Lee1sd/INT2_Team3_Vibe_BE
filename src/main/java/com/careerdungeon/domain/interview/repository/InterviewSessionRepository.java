package com.careerdungeon.domain.interview.repository;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.InterviewSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    /**
     * 사용자의 가장 최근 면접 세션 1건을 조회한다. createdAt이 같으면 id 내림차순으로 판단한다.
     *
     * <p>파생 쿼리 이름({@code findFirstByUserIdOrderByCreatedAtDescIdDesc})만으로는 Spring Data가
     * {@link InterviewSession#getUserId()} 편의 메서드를 실제 JPA 속성으로 오인해 {@code user.id}
     * 탐색으로 자동 폴백하지 못하는 문제가 있어, {@link #findRecentSessionsByUserId} 명시적
     * JPQL과 {@link Pageable}(최대 1건) 조합으로 우회한다.
     */
    default Optional<InterviewSession> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId) {
        List<InterviewSession> results = findRecentSessionsByUserId(userId, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("select s from InterviewSession s where s.user.id = :userId order by s.createdAt desc, s.id desc")
    List<InterviewSession> findRecentSessionsByUserId(@Param("userId") Long userId, Pageable pageable);

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
