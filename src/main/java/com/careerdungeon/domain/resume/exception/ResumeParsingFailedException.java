package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeParsingFailedException extends BusinessException {
    public ResumeParsingFailedException(String message) {
        super("RESUME_PARSING_FAILED", message, HttpStatus.BAD_REQUEST);
    }

    public ResumeParsingFailedException(String message, Throwable cause) {
        super("RESUME_PARSING_FAILED", message, HttpStatus.BAD_REQUEST);
        initCause(cause);
    }
}
