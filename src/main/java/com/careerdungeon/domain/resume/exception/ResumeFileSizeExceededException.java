package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeFileSizeExceededException extends BusinessException {

    public ResumeFileSizeExceededException() {
        super("RESUME_FILE_SIZE_EXCEEDED", "이력서 파일은 비어 있지 않아야 하며 최대 10MB까지 업로드할 수 있습니다.",
                HttpStatus.BAD_REQUEST);
    }
}
