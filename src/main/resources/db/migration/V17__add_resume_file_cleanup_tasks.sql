CREATE TABLE `resume_file_cleanup_tasks` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `resume_id` BIGINT NOT NULL,
    `s3_key` VARCHAR(255) NOT NULL,
    `created_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_RESUME_FILE_CLEANUP_TASK_RESUME`
        FOREIGN KEY (`resume_id`) REFERENCES `resumes` (`id`) ON DELETE CASCADE
);

CREATE INDEX `IDX_RESUME_FILE_CLEANUP_TASK_CREATED_AT`
    ON `resume_file_cleanup_tasks` (`created_at`);
