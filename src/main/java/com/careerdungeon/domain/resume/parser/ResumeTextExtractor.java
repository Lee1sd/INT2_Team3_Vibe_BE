package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;

public interface ResumeTextExtractor {

    String extract(String s3Key) throws ResumeParsingFailedException;
}
