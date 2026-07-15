-- 뱃지 기준 Stage와 사용자별 중복 지급을 DB 제약으로도 방어한다.
ALTER TABLE `badges`
    ADD CONSTRAINT `UK_badges_stage` UNIQUE (`stage`);

ALTER TABLE `badges`
    ADD CONSTRAINT `CHK_badges_stage`
        CHECK (`stage` BETWEEN 1 AND 4);

ALTER TABLE `user_badges`
    ADD CONSTRAINT `UK_user_badges_user_badge` UNIQUE (`user_id`, `badge_id`);
