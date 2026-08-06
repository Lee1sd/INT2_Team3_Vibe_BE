-- resumes.extracted_text가 MySQL TEXT(최대 65,535바이트 ≈ 64KB)라서, 파일 업로드 허용
-- 크기(10MB)에 가까운 .txt/.md 이력서를 올리면 추출된 텍스트가 컬럼 한계를 넘어 저장이
-- 실패하거나 조용히 잘릴 수 있었다(이슈 #193). MEDIUMTEXT(최대 16,777,215바이트 ≈ 16MB)로
-- 넓혀서 10MB 업로드 한도 내에서는 항상 저장에 성공하도록 한다.
--
-- MySQL 전용 MODIFY COLUMN 구문이라 H2(MODE=MySQL)가 파싱하지 못한다 — V14와 동일하게
-- `/*! ... */`(MySQL 조건부 주석)로 감싸서 H2에서는 단순 주석으로 무시되게 한다.
/*! ALTER TABLE `resumes`
    MODIFY COLUMN `extracted_text` MEDIUMTEXT NULL */;
