ALTER TABLE `resumes`
    ADD COLUMN `deleted_at` TIMESTAMP NULL;

CREATE INDEX `IDX_RESUMES_USER_TYPE_DELETED_AT`
    ON `resumes` (`user_id`, `type`, `deleted_at`);
