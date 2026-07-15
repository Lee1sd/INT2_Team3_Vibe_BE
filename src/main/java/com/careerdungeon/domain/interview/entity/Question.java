package com.careerdungeon.domain.interview.entity;

import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "message_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_messages_TO_questions_1"))
    private Message message;

    @Lob
    @Column(name = "expected_answer", nullable = false, columnDefinition = "TEXT")
    private String expectedAnswer;

    protected Question() {
    }

    public Question(Message message, String expectedAnswer) {
        Message questionMessage = Objects.requireNonNull(message, "message must not be null");
        if (questionMessage.getRole() == MessageRole.ANSWER) {
            throw new IllegalArgumentException("question message must not have ANSWER role");
        }
        if (expectedAnswer == null || expectedAnswer.isBlank()) {
            throw new IllegalArgumentException("expectedAnswer must not be blank");
        }
        this.message = questionMessage;
        this.expectedAnswer = expectedAnswer;
    }

    public Long getMessageId() {
        return messageId;
    }

    public Message getMessage() {
        return message;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }
}
