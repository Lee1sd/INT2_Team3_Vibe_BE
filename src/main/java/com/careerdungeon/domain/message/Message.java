package com.careerdungeon.domain.message;

import com.careerdungeon.domain.interview.entity.InterviewSession;
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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "messages",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_MESSAGES_SESSION_ROLE_TURN",
                columnNames = {"session_id", "role", "turn"}))
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "session_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_interview_sessions_TO_messages_1"))
    private InterviewSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int turn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(InterviewSession session, MessageRole role, String content, int turn) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (turn < 1) {
            throw new IllegalArgumentException("turn must be positive");
        }
        this.content = content;
        this.turn = turn;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public InterviewSession getSession() {
        return session;
    }

    public Long getSessionId() {
        return session.getId();
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public int getTurn() {
        return turn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
