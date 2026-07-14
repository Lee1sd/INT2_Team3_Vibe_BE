CREATE TABLE `persona_config` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `level`    INT    NOT NULL    COMMENT '1=널널한 대리, 2=깐깐한 과장, 3=압박 페르소나 - Lv.3은 MVP에서 UI만 노출(회색/잠금), 실제 면접 로직 없음',
    `tone`    VARCHAR(20)    NOT NULL
);

CREATE TABLE `users` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `google_id`    VARCHAR(255)    NOT NULL UNIQUE    COMMENT 'Google OAuth2 sub 값',
    `email`    VARCHAR(255)    NOT NULL,
    `name`    VARCHAR(100)    NOT NULL,
    `created_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `user_unlock_status` (
    `user_id`    BIGINT    NOT NULL PRIMARY KEY,
    `unlocked_level`    INT    NOT NULL    DEFAULT 1    COMMENT '해금 기준 80% (2장 A3 확정)',
    `progress_gauge`    INT    NOT NULL    DEFAULT 0    COMMENT '오늘 신규: 메인페이지 진행도 게이지(%). 레벨 클리어마다 +30, 판정 점수와는 별개'
);

CREATE TABLE `badges` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `stage`    INT    NOT NULL    COMMENT '1~4단계 (오늘 확정: 4개)',
    `name`    VARCHAR(100)    NOT NULL    COMMENT '예: 프로그램 머쓱',
    `image_url`    VARCHAR(255)    NOT NULL,
    `unlock_condition`    VARCHAR(50)    NOT NULL    COMMENT 'SIGNUP / LEVEL1_UNLOCK / LEVEL2_UNLOCK / LEVEL3_UNLOCK'
);

CREATE TABLE `resumes` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT    NOT NULL,
    `type`    ENUM('RESUME', 'PORTFOLIO')    NOT NULL    DEFAULT 'RESUME'    COMMENT '이력서(필수)/포트폴리오(선택) 구분 - 오늘 확정',
    `s3_key`    VARCHAR(255)    NOT NULL,
    `extracted_text`    TEXT    NULL    COMMENT '⚠️ 이름/연락처/이메일 등 PII는 추출 즉시 마스킹 처리 후 저장 (마스킹 대상 항목·방식은 팀 합의 필요)',
    `parse_status`    VARCHAR(20)    NOT NULL    COMMENT 'PROCESSING/DONE/FAILED - API 응답 표기와 통일',
    `file_hash`    VARCHAR(64)    NOT NULL    COMMENT 'SHA-256 캐싱 키',
    `cache_expires_at`    TIMESTAMP    NULL    COMMENT '캐시 만료 시각 (TTL 계산은 서비스 로직)',
    `created_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `interview_sessions` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT    NOT NULL,
    `resume_id`    BIGINT    NOT NULL    COMMENT '반드시 type=RESUME인 레코드만 참조 (포트폴리오 단독으로는 세션 생성 불가)',
    `persona_config_id`    BIGINT    NOT NULL,
    `selected_keyword`    ENUM('데이터전처리', 'DB', '부하', '보안', '시스템설계', '클라우드')    NOT NULL,
    `status`    VARCHAR(20)    NOT NULL    COMMENT 'IN_PROGRESS/AWAITING_FOLLOWUP/COMPLETED',
    `created_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `messages` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `session_id`    BIGINT    NOT NULL,
    `role`    VARCHAR(20)    NOT NULL    COMMENT 'QUESTION/ANSWER',
    `content`    TEXT    NOT NULL,
    `turn`    INT    NOT NULL    COMMENT '멀티턴 윈도잉 기준. 채팅 히스토리 사이드바 조회에도 사용',
    `created_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `answer_scores` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `session_id`    BIGINT    NOT NULL    COMMENT '세션당 최대 4건 (질문3+꼬리질문1)',
    `turn`    INT    NOT NULL    COMMENT '1~4',
    `score`    INT    NOT NULL    COMMENT '문항당 0~25점',
    `is_follow_up`    BOOLEAN    NOT NULL    DEFAULT FALSE,
    `created_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `judgment_results` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `session_id`    BIGINT    NOT NULL    COMMENT '세션당 1건(최종 판정)',
    `total_score`    INT    NOT NULL    COMMENT '4문항 합산 (0~100). 결과화면엔 % 환산 없이 총점 그대로 표시',
    `passed`    BOOLEAN    NOT NULL    COMMENT '80점 이상 여부',
    `overall_feedback`    TEXT    NOT NULL    COMMENT '3개 답변 종합 피드백, 사용자 노출',
    `created_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `refresh_tokens` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT    NOT NULL,
    `token_hash`    VARCHAR(255)    NOT NULL,
    `expires_at`    TIMESTAMP    NOT NULL    COMMENT '7일 만료',
    `revoked`    BOOLEAN    NOT NULL    DEFAULT FALSE    COMMENT '로그아웃 시 true',
    `created_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `user_badges` (
    `id`    BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT    NOT NULL,
    `badge_id`    BIGINT    NOT NULL,
    `acquired_at`    TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE `user_unlock_status` ADD CONSTRAINT `FK_users_TO_user_unlock_status_1` FOREIGN KEY (
    `user_id`
)
REFERENCES `users` (
    `id`
);

ALTER TABLE `resumes` ADD CONSTRAINT `FK_users_TO_resumes_1` FOREIGN KEY (
    `user_id`
)
REFERENCES `users` (
    `id`
);

ALTER TABLE `refresh_tokens` ADD CONSTRAINT `FK_users_TO_refresh_tokens_1` FOREIGN KEY (
    `user_id`
)
REFERENCES `users` (
    `id`
);

ALTER TABLE `user_badges` ADD CONSTRAINT `FK_users_TO_user_badges_1` FOREIGN KEY (
    `user_id`
)
REFERENCES `users` (
    `id`
);

ALTER TABLE `user_badges` ADD CONSTRAINT `FK_badges_TO_user_badges_1` FOREIGN KEY (
    `badge_id`
)
REFERENCES `badges` (
    `id`
);

ALTER TABLE `interview_sessions` ADD CONSTRAINT `FK_users_TO_interview_sessions_1` FOREIGN KEY (
    `user_id`
)
REFERENCES `users` (
    `id`
);

ALTER TABLE `interview_sessions` ADD CONSTRAINT `FK_resumes_TO_interview_sessions_1` FOREIGN KEY (
    `resume_id`
)
REFERENCES `resumes` (
    `id`
);

ALTER TABLE `interview_sessions` ADD CONSTRAINT `FK_persona_config_TO_interview_sessions_1` FOREIGN KEY (
    `persona_config_id`
)
REFERENCES `persona_config` (
    `id`
);

ALTER TABLE `messages` ADD CONSTRAINT `FK_interview_sessions_TO_messages_1` FOREIGN KEY (
    `session_id`
)
REFERENCES `interview_sessions` (
    `id`
);

ALTER TABLE `answer_scores` ADD CONSTRAINT `FK_interview_sessions_TO_answer_scores_1` FOREIGN KEY (
    `session_id`
)
REFERENCES `interview_sessions` (
    `id`
);

ALTER TABLE `judgment_results` ADD CONSTRAINT `FK_interview_sessions_TO_judgment_results_1` FOREIGN KEY (
    `session_id`
)
REFERENCES `interview_sessions` (
    `id`
);

ALTER TABLE `judgment_results` ADD CONSTRAINT `UK_judgment_results_session_id` UNIQUE (
    `session_id`
);
