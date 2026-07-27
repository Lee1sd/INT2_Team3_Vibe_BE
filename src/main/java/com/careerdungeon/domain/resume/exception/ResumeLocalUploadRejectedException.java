package com.careerdungeon.domain.resume.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 로컬 업로드 요청이 발급 당시의 크기 또는 Content-Type과 다를 때 반환하는 예외다.
 */
public class ResumeLocalUploadRejectedException extends BusinessException {

    /** 클라이언트가 새 URL부터 다시 발급할 수 있도록 고정된 400 계약을 제공한다. */
    public ResumeLocalUploadRejectedException() {
        super("RESUME_LOCAL_UPLOAD_REJECTED",
                "발급된 업로드 정보와 파일이 일치하지 않습니다. 업로드 URL을 다시 발급해 주세요.",
                HttpStatus.BAD_REQUEST);
    }
}
