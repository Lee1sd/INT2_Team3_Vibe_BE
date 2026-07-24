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
