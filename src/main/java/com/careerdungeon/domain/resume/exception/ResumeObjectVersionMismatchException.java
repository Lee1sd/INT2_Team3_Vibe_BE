package com.careerdungeon.domain.resume.exception;

public class ResumeObjectVersionMismatchException extends ResumeStorageException {

    public ResumeObjectVersionMismatchException(Throwable cause) {
        super("Uploaded resume object version changed.", cause);
    }
}
