CREATE TABLE `questions` (
    `message_id`    BIGINT    NOT NULL PRIMARY KEY    COMMENT '질문 메시지(messages, role=QUESTION)와 1:1 — 질문 본문은 messages.content에 이미 있으므로 여기 별도 저장하지 않음',
    `expected_answer`    TEXT    NOT NULL    COMMENT '질문 생성 호출(FR-03)에서 함께 생성한 모범답안. 채점 호출(FR-04)에서 재사용, 새로 생성하지 않음. API·화면에 노출 안 함(채점 로직 내부 전용)'
);

ALTER TABLE `questions` ADD CONSTRAINT `FK_messages_TO_questions_1` FOREIGN KEY (
    `message_id`
)
REFERENCES `messages` (
    `id`
);
