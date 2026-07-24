-- 기존 3+1문항 채점 결과는 새 4+1문항 계약으로 무손실 변환할 수 없다.
-- 운영 전 폐기 가능한 개발 데이터라는 팀 합의에 따라 면접 이력이 있는 사용자의
-- 진행도와 Stage2 이상 뱃지를 가입 직후 상태로 되돌린 뒤 기존 면접 세션을 제거한다.
DELETE FROM `user_badges`
WHERE `user_id` IN (
    SELECT DISTINCT `user_id`
    FROM `interview_sessions`
)
AND `badge_id` IN (
    SELECT `id`
    FROM `badges`
    WHERE `stage` BETWEEN 2 AND 4
);

UPDATE `user_unlock_status`
SET `unlocked_level` = 1,
    `progress_gauge` = 0
WHERE `user_id` IN (
    SELECT DISTINCT `user_id`
    FROM `interview_sessions`
);

-- V11의 ON DELETE CASCADE로 메시지·질문·문항점수·최종판정도 함께 제거한다.
DELETE FROM `interview_sessions`;

-- 최초 turn 1~4와 꼬리질문 turn 5의 새 문항·배점 계약으로 CHECK 제약을 교체한다.
ALTER TABLE `answer_scores`
    DROP CONSTRAINT `CK_ANSWER_SCORES_TURN`;

ALTER TABLE `answer_scores`
    DROP CONSTRAINT `CK_ANSWER_SCORES_SCORE`;

ALTER TABLE `answer_scores`
    DROP CONSTRAINT `CK_ANSWER_SCORES_FOLLOW_UP`;

ALTER TABLE `answer_scores`
    ADD CONSTRAINT `CK_ANSWER_SCORES_TURN` CHECK (`turn` BETWEEN 1 AND 5);

ALTER TABLE `answer_scores`
    ADD CONSTRAINT `CK_ANSWER_SCORES_SCORE` CHECK (`score` BETWEEN 0 AND 20);

ALTER TABLE `answer_scores`
    ADD CONSTRAINT `CK_ANSWER_SCORES_FOLLOW_UP` CHECK (
        (`turn` = 5 AND `is_follow_up` = TRUE)
        OR (`turn` BETWEEN 1 AND 4 AND `is_follow_up` = FALSE)
    );

-- 레벨은 interview_sessions에 있어 단일 테이블 CHECK로 passed를 검증할 수 없으므로
-- 기존 80점 고정 제약을 제거하고 레벨별 판정 일치는 애플리케이션에서 강제한다.
ALTER TABLE `judgment_results`
    DROP CONSTRAINT `CK_JUDGMENT_RESULTS_PASSED`;
