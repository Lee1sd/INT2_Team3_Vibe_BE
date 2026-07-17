package com.careerdungeon.domain.interview.entity;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.resume.entity.Resume;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_users_TO_interview_sessions_1"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "resume_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_resumes_TO_interview_sessions_1"))
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "persona_config_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_persona_config_TO_interview_sessions_1"))
    private PersonaConfig personaConfig;

    @Column(name = "selected_keyword", nullable = false, length = 20)
    private String selectedKeyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewSessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InterviewSession() {
    }

    public InterviewSession(User user, Resume resume, PersonaConfig personaConfig, String selectedKeyword) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.resume = Objects.requireNonNull(resume, "resume must not be null");
        this.personaConfig = Objects.requireNonNull(personaConfig, "personaConfig must not be null");
        if (selectedKeyword == null || selectedKeyword.isBlank()) {
            throw new IllegalArgumentException("selectedKeyword must not be blank");
        }
        this.selectedKeyword = selectedKeyword;
        this.status = InterviewSessionStatus.IN_PROGRESS;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    public Resume getResume() {
        return resume;
    }

    public Long getResumeId() {
        return resume.getId();
    }

    public PersonaConfig getPersonaConfig() {
        return personaConfig;
    }

    public Long getPersonaConfigId() {
        return personaConfig.getId();
    }

    public String getSelectedKeyword() {
        return selectedKeyword;
    }

    public InterviewSessionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void startInitialScoring() {
        this.status = InterviewSessionStatus.SCORING_INITIAL;
    }

    public void resetToInProgress() {
        this.status = InterviewSessionStatus.IN_PROGRESS;
    }

    public void awaitFollowup() {
        this.status = InterviewSessionStatus.AWAITING_FOLLOWUP;
    }

    public void startFinalScoring() {
        this.status = InterviewSessionStatus.SCORING_FINAL;
    }

    public void resetToAwaitingFollowup() {
        this.status = InterviewSessionStatus.AWAITING_FOLLOWUP;
    }

    public void complete() {
        this.status = InterviewSessionStatus.COMPLETED;
    }
}
