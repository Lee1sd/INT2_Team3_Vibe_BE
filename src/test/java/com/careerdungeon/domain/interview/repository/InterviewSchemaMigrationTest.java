package com.careerdungeon.domain.interview.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewSchemaMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    @Test
    @DisplayName("V1 messages/interview_sessions 컬럼과 엔티티 FK 필드가 일치한다")
    void v1ContainsInterviewSessionAndMessageColumns() throws IOException {
        String v1 = readMigration("V1__init.sql");

        assertThat(v1).contains("CREATE TABLE `interview_sessions`");
        assertThat(v1).contains("`user_id`");
        assertThat(v1).contains("`resume_id`");
        assertThat(v1).contains("`persona_config_id`");
        assertThat(v1).contains("`selected_keyword`");
        assertThat(v1).contains("`status`");
        assertThat(v1).contains("`created_at`");

        assertThat(v1).contains("CREATE TABLE `messages`");
        assertThat(v1).contains("`session_id`");
        assertThat(v1).contains("`role`");
        assertThat(v1).contains("`content`");
        assertThat(v1).contains("`turn`");
        assertThat(v1).contains("FK_interview_sessions_TO_messages_1");
    }

    @Test
    @DisplayName("V4 questions 테이블은 message_id 단일 PK/FK와 expected_answer만 가진다")
    void v4QuestionsTableUsesMessageIdPrimaryKey() throws IOException {
        String v4 = readMigration("V4__add_questions_table.sql");

        assertThat(v4).contains("CREATE TABLE `questions`");
        assertThat(v4).contains("`message_id`    BIGINT    NOT NULL PRIMARY KEY");
        assertThat(v4).contains("`expected_answer`    TEXT    NOT NULL");
        assertThat(v4).contains("FK_messages_TO_questions_1");
        assertThat(v4).doesNotContain("session_id");
        assertThat(v4).doesNotContain("question_id");
        assertThat(v4).doesNotContain("question_text");
    }

    @Test
    @DisplayName("V5 messages 테이블에 session_id, role, turn 복합 UNIQUE를 추가한다")
    void v5AddsMessageTurnInvariant() throws IOException {
        String v5 = readMigration("V5__add_messages_session_role_turn_unique.sql");

        assertThat(v5).contains("UQ_MESSAGES_SESSION_ROLE_TURN");
        assertThat(v5).contains("`session_id`");
        assertThat(v5).contains("`role`");
        assertThat(v5).contains("`turn`");
    }

    private static String readMigration(String fileName) throws IOException {
        return Files.readString(MIGRATION_DIR.resolve(fileName));
    }
}
