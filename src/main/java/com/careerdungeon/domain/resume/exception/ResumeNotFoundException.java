package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeNotFoundException extends BusinessException {
    public ResumeNotFoundException(Long resumeId) {
        super("RESUME_NOT_FOUND", "이력서를 찾을 수 없습니다. (id: " + resumeId + ")", HttpStatus.NOT_FOUND);
    }
}
