package com.careerdungeon.domain.judgment.llm.dto;

/**
 * LLM이 반환하는 5개 루브릭 원시값. 누락된 JSON 필드를 검출할 수 있도록 boxed 타입을 쓴다.
 *
 * @param technicalAccuracy 기술적 정확성 원시 점수(의도 범위 0~10)
 * @param coreCoverage 핵심 내용 충족도 원시 점수(의도 범위 0~5)
 * @param reasoning 근거·판단 과정 원시 점수(의도 범위 0~4)
 * @param specificity 구체성·실무 연계 원시 점수(의도 범위 0~3)
 * @param tradeOffsAndExceptions 트레이드오프·예외 원시 점수(의도 범위 0~3)
 */
public record RubricScores(
        Integer technicalAccuracy,
        Integer coreCoverage,
        Integer reasoning,
        Integer specificity,
        Integer tradeOffsAndExceptions
) {
}
