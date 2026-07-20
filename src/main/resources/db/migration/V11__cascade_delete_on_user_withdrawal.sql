-- 회원 탈퇴는 "전체 즉시 삭제"로 이미 합의되어 있다(privacy-policy.md, security-design.md).
-- users 행 하나를 지우면 이 체인 전체가 DB 레벨에서 함께 지워지도록 기존 FK를
-- ON DELETE CASCADE로 재정의한다(ADR-015 참고). 공유 참조 테이블(badges, persona_config)로
-- 향하는 FK는 그대로 둔다 — 유저 데이터가 아니라 여러 유저가 같이 참조하는 마스터 데이터라
-- 캐스케이드 대상이 아니다.
--
-- users
--  ├─ user_unlock_status
--  ├─ resumes ── interview_sessions
--  ├─ refresh_tokens
--  ├─ user_badges
--  └─ interview_sessions
--       ├─ messages ── questions
--       ├─ answer_scores
--       └─ judgment_results

ALTER TABLE `user_unlock_status` DROP CONSTRAINT `FK_users_TO_user_unlock_status_1`;
ALTER TABLE `user_unlock_status` ADD CONSTRAINT `FK_users_TO_user_unlock_status_1`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

ALTER TABLE `resumes` DROP CONSTRAINT `FK_users_TO_resumes_1`;
ALTER TABLE `resumes` ADD CONSTRAINT `FK_users_TO_resumes_1`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

ALTER TABLE `refresh_tokens` DROP CONSTRAINT `FK_users_TO_refresh_tokens_1`;
ALTER TABLE `refresh_tokens` ADD CONSTRAINT `FK_users_TO_refresh_tokens_1`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

ALTER TABLE `user_badges` DROP CONSTRAINT `FK_users_TO_user_badges_1`;
ALTER TABLE `user_badges` ADD CONSTRAINT `FK_users_TO_user_badges_1`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

ALTER TABLE `interview_sessions` DROP CONSTRAINT `FK_users_TO_interview_sessions_1`;
ALTER TABLE `interview_sessions` ADD CONSTRAINT `FK_users_TO_interview_sessions_1`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

ALTER TABLE `interview_sessions` DROP CONSTRAINT `FK_resumes_TO_interview_sessions_1`;
ALTER TABLE `interview_sessions` ADD CONSTRAINT `FK_resumes_TO_interview_sessions_1`
    FOREIGN KEY (`resume_id`) REFERENCES `resumes` (`id`) ON DELETE CASCADE;

ALTER TABLE `messages` DROP CONSTRAINT `FK_interview_sessions_TO_messages_1`;
ALTER TABLE `messages` ADD CONSTRAINT `FK_interview_sessions_TO_messages_1`
    FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`) ON DELETE CASCADE;

ALTER TABLE `answer_scores` DROP CONSTRAINT `FK_interview_sessions_TO_answer_scores_1`;
ALTER TABLE `answer_scores` ADD CONSTRAINT `FK_interview_sessions_TO_answer_scores_1`
    FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`) ON DELETE CASCADE;

ALTER TABLE `judgment_results` DROP CONSTRAINT `FK_interview_sessions_TO_judgment_results_1`;
ALTER TABLE `judgment_results` ADD CONSTRAINT `FK_interview_sessions_TO_judgment_results_1`
    FOREIGN KEY (`session_id`) REFERENCES `interview_sessions` (`id`) ON DELETE CASCADE;

ALTER TABLE `questions` DROP CONSTRAINT `FK_messages_TO_questions_1`;
ALTER TABLE `questions` ADD CONSTRAINT `FK_messages_TO_questions_1`
    FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`) ON DELETE CASCADE;
