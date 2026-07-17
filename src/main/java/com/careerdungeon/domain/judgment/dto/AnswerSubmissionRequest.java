package com.careerdungeon.domain.judgment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** IS-002 최초 세 답변 또는 IS-002b 꼬리질문 답변 요청이다. */
public record AnswerSubmissionRequest(
        @NotEmpty(message = "answers는 한 건 이상 필요합니다.")
        List<@Valid SubmittedAnswerRequest> answers
) {
    /** 외부 변경으로 검증 결과가 달라지지 않도록 요청 목록을 복사한다. */
    public AnswerSubmissionRequest {
        if (answers != null) {
            // null 항목은 Bean Validation/서비스가 명시적 400으로 처리하도록 보존한다.
            answers = Collections.unmodifiableList(new ArrayList<>(answers));
        }
    }
}
