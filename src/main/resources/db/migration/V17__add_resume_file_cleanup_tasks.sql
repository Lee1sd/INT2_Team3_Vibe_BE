CREATE TABLE `resume_file_cleanup_tasks` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `resume_id` BIGINT NOT NULL,
    `s3_key` VARCHAR(255) NOT NULL,
    `created_at` TIMESTAMP NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `last_attempt_at` TIMESTAMP NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `IDX_RESUME_FILE_CLEANUP_TASK_CREATED_AT`
    ON `resume_file_cleanup_tasks` (`created_at`);
