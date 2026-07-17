package com.careerdungeon.domain.judgment.dto;

/** 세션 상태에 따라 달라지는 최초·최종 답변 제출 응답의 공통 타입이다. */
public sealed interface AnswerSubmissionResponse
        permits InitialAnswerSubmissionResponse, FinalAnswerSubmissionResponse {
}
