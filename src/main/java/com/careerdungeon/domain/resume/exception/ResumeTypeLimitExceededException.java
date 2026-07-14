package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeTypeLimitExceededException extends BusinessException {

    public ResumeTypeLimitExceededException(ResumeType type) {
        super("RESUME_TYPE_LIMIT_EXCEEDED", type + "는 최대 3개까지만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST);
    }
}
