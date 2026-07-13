package com.careerdungeon.domain.judgment.llm.dto;

/**
 * LLM이 반환하는 5개 루브릭 원시값. 누락된 JSON 필드를 검출할 수 있도록 boxed 타입을 쓴다.
 */
public record RubricScores(
        Integer technicalAccuracy,
        Integer coreCoverage,
        Integer reasoning,
        Integer specificity,
        Integer tradeOffsAndExceptions
) {
}
