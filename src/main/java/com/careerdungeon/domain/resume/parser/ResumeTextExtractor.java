package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;

// TODO: PDFBox 기반 구현체 필요 (S3에서 s3Key로 원본을 내려받아 텍스트 추출).
// 구현체 빈이 없으면 ResumeParsingService 주입 시점에 애플리케이션 컨텍스트 기동이 실패한다.
public interface ResumeTextExtractor {

    String extract(String s3Key) throws ResumeParsingFailedException;
}
