ALTER TABLE `judgment_results`
    ADD CONSTRAINT `CK_JUDGMENT_RESULTS_PASSED` CHECK (
        (`total_score` >= 80 AND `passed` = TRUE)
        OR (`total_score` < 80 AND `passed` = FALSE)
    );
