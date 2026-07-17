package com.careerdungeon.domain.judgment.entity;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.Objects;

/** 서버가 루브릭으로 확정한 세션별 문항 점수와 피드백을 보존한다. */
@Entity
@Table(
        name = "answer_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_ANSWER_SCORES_SESSION_TURN",
                columnNames = {"session_id", "turn"}))
@Check(constraints = "turn between 1 and 4 and score between 0 and 25")
public class AnswerScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "session_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_interview_sessions_TO_answer_scores_1"))
    private InterviewSession session;

    @Column(nullable = false)
    private int turn;

    @Column(nullable = false)
    private int score;

    @Column(name = "is_follow_up", nullable = false)
    private boolean followUp;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA가 엔티티를 복원할 때 사용하는 기본 생성자다. */
    protected AnswerScore() {
    }

    private AnswerScore(InterviewSession session, int turn, int score, String feedback) {
        this.session = Objects.requireNonNull(session, "면접 세션은 필수입니다.");
        if (turn < 1 || turn > 4) {
            throw new IllegalArgumentException("turn은 1~4여야 합니다.");
        }
        if (score < 0 || score > 25) {
            throw new IllegalArgumentException("문항 점수는 0~25여야 합니다.");
        }
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("문항 피드백은 필수입니다.");
        }
        this.turn = turn;
        this.score = score;
        this.followUp = turn == 4;
        this.feedback = feedback;
        this.createdAt = Instant.now();
    }

    /** judgment가 확정한 문항 점수를 영속 엔티티로 변환한다. */
    public static AnswerScore from(InterviewSession session, QuestionScore questionScore) {
        Objects.requireNonNull(questionScore, "확정 문항 점수는 필수입니다.");
        return new AnswerScore(
                session,
                questionScore.questionId(),
                questionScore.score(),
                questionScore.feedback());
    }

    /** 점수 레코드 식별자를 반환한다. */
    public Long getId() {
        return id;
    }

    /** 점수가 속한 면접 세션 식별자를 반환한다. */
    public Long getSessionId() {
        return session.getId();
    }

    /** 세션 내부 문항 순서인 turn을 반환한다. */
    public int getTurn() {
        return turn;
    }

    /** 서버 확정 문항 점수를 반환한다. */
    public int getScore() {
        return score;
    }

    /** 꼬리질문 turn 4 점수인지 반환한다. */
    public boolean isFollowUp() {
        return followUp;
    }

    /** 최종 종합 피드백 컨텍스트에 재사용할 개별 피드백을 반환한다. */
    public String getFeedback() {
        return feedback;
    }
}
