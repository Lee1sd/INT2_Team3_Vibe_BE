ALTER TABLE resumes
    ADD COLUMN original_file_name VARCHAR(255) NULL;

ALTER TABLE resumes
    ADD COLUMN file_size BIGINT NULL;
