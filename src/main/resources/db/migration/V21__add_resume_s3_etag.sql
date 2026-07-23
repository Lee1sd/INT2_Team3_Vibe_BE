-- 완료 API에서 검증한 S3 객체 버전을 비동기 파싱까지 동일하게 유지한다.
-- 기존 Resume 레코드에는 ETag가 없으므로 하위 호환을 위해 NULL을 허용한다.
ALTER TABLE resumes
    ADD COLUMN s3_etag VARCHAR(255) NULL;

-- 원본 삭제 실패 후 재시도할 때도 같은 객체 버전만 조건부 삭제하도록 ETag를 보존한다.
-- cleanup task는 Resume 삭제·회원 탈퇴 후에도 독립적으로 실행되므로 별도 컬럼이 필요하다.
ALTER TABLE resume_file_cleanup_tasks
    ADD COLUMN s3_etag VARCHAR(255) NULL;
