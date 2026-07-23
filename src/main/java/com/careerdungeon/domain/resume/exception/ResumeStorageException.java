package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeStorageException extends BusinessException {

    public ResumeStorageException(String message) {
        super("RESUME_STORAGE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ResumeStorageException(String message, Throwable cause) {
        super("RESUME_STORAGE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}
