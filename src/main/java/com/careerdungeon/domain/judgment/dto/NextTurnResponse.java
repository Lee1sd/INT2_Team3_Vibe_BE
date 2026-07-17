package com.careerdungeon.domain.judgment.dto;

/** 최초 채점 뒤 사용자에게 제시할 꼬리질문 정보를 반환한다. */
public record NextTurnResponse(
        String type,
        long targetQuestionId,
        String question
) {
    private static final String FOLLOW_UP_TYPE = "FOLLOW_UP";

    /** 최저점 원문 질문과 새로 생성된 꼬리질문을 API 계약으로 조립한다. */
    public static NextTurnResponse followUp(long targetQuestionId, String question) {
        return new NextTurnResponse(FOLLOW_UP_TYPE, targetQuestionId, question);
    }
}
