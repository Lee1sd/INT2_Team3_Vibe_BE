package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ResumeUploadNotFoundException extends BusinessException {

    public ResumeUploadNotFoundException() {
        super("RESUME_UPLOAD_NOT_FOUND", "완료할 이력서 업로드를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }
}
