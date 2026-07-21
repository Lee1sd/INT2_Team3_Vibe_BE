ALTER TABLE `users`
    ADD COLUMN `profile_image_key` VARCHAR(255) NULL COMMENT 'S3 object key (마이페이지 프로필 이미지, ADR-018). URL이 아니라 key만 저장';
