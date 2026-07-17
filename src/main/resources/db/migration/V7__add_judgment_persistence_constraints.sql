ALTER TABLE `answer_scores`
    ADD COLUMN `feedback` TEXT NOT NULL DEFAULT ('기존 채점 피드백이 없습니다.');

ALTER TABLE `answer_scores`
    ALTER COLUMN `feedback` DROP DEFAULT;

ALTER TABLE `answer_scores`
    ADD CONSTRAINT `UQ_ANSWER_SCORES_SESSION_TURN` UNIQUE (`session_id`, `turn`);

ALTER TABLE `answer_scores`
    ADD CONSTRAINT `CK_ANSWER_SCORES_TURN` CHECK (`turn` BETWEEN 1 AND 4);

ALTER TABLE `answer_scores`
    ADD CONSTRAINT `CK_ANSWER_SCORES_SCORE` CHECK (`score` BETWEEN 0 AND 25);

ALTER TABLE `answer_scores`
    ADD CONSTRAINT `CK_ANSWER_SCORES_FOLLOW_UP` CHECK (
        (`turn` = 4 AND `is_follow_up` = TRUE)
        OR (`turn` BETWEEN 1 AND 3 AND `is_follow_up` = FALSE)
    );

ALTER TABLE `judgment_results`
    ADD CONSTRAINT `CK_JUDGMENT_RESULTS_TOTAL_SCORE` CHECK (`total_score` BETWEEN 0 AND 100);
