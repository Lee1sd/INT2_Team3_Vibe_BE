package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeObjectVersionMismatchException extends BusinessException {

    public ResumeObjectVersionMismatchException(Throwable cause) {
        super("RESUME_OBJECT_VERSION_CONFLICT",
                "업로드된 파일이 변경되었습니다. 업로드 URL을 다시 발급해 주세요.",
                HttpStatus.CONFLICT);
        initCause(cause);
    }
}
