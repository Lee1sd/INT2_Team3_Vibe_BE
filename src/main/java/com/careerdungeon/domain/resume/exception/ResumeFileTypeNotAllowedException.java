package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeFileTypeNotAllowedException extends BusinessException {
    public ResumeFileTypeNotAllowedException(String filename) {
        super("RESUME_FILE_TYPE_NOT_ALLOWED",
                "지원하지 않는 파일 형식입니다. PDF/TXT/MD 파일만 업로드할 수 있습니다. (파일명: " + filename + ")",
                HttpStatus.BAD_REQUEST);
    }
}
