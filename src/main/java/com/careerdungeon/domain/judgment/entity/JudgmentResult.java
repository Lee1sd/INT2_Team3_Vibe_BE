package com.careerdungeon.domain.judgment.entity;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.Objects;

/** 세션당 한 번 생성되는 서버 최종 판정과 종합 피드백을 보존한다. */
@Entity
@Table(
        name = "judgment_results",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_judgment_results_session_id",
                columnNames = "session_id"))
@Check(constraints = "total_score between 0 and 100 and "
        + "((total_score >= 80 and passed = true) or (total_score < 80 and passed = false))")
public class JudgmentResult {

    private static final int PASSING_SCORE = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "session_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "FK_interview_sessions_TO_judgment_results_1"))
    private InterviewSession session;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(nullable = false)
    private boolean passed;

    @Lob
    @Column(name = "overall_feedback", nullable = false, columnDefinition = "TEXT")
    private String overallFeedback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA가 엔티티를 복원할 때 사용하는 기본 생성자다. */
    protected JudgmentResult() {
    }

    private JudgmentResult(
            InterviewSession session,
            int totalScore,
            String overallFeedback) {
        this.session = Objects.requireNonNull(session, "면접 세션은 필수입니다.");
        if (totalScore < 0 || totalScore > 100) {
            throw new IllegalArgumentException("최종 점수는 0~100이어야 합니다.");
        }
        if (overallFeedback == null || overallFeedback.isBlank()) {
            throw new IllegalArgumentException("종합 피드백은 필수입니다.");
        }
        this.totalScore = totalScore;
        this.passed = totalScore >= PASSING_SCORE;
        this.overallFeedback = overallFeedback;
        this.createdAt = Instant.now();
    }

    /** 서버 확정 최종 평가를 세션당 단일 판정 엔티티로 변환한다. */
    public static JudgmentResult from(
            InterviewSession session,
            FinalJudgmentEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "최종 평가는 필수입니다.");
        return new JudgmentResult(
                session,
                evaluation.totalScore(),
                evaluation.overallFeedback());
    }

    /** 최종 판정 식별자를 반환한다. */
    public Long getId() {
        return id;
    }

    /** 최종 판정이 속한 면접 세션 식별자를 반환한다. */
    public Long getSessionId() {
        return session.getId();
    }

    /** 서버가 재계산한 0~100 최종 점수를 반환한다. */
    public int getTotalScore() {
        return totalScore;
    }

    /** 최종 점수가 80점 이상인지 반환한다. */
    public boolean isPassed() {
        return passed;
    }

    /** 사용자에게 노출할 종합 피드백을 반환한다. */
    public String getOverallFeedback() {
        return overallFeedback;
    }
}
