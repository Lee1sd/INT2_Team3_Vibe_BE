package com.careerdungeon.global.llm.dto;

/**
 * 문항 하나에 대한 LLM 평가 결과.
 *
 * @param turn                    질문 순서 (1~4)
 * @param score                   LLM이 반환한 원시 합산 점수 (0~25 의도, 범위 이탈 가능) — clamp는 ③의 책임
 * @param technicalAccuracy       기술적 정확성 원시 점수 (의도 범위 0~10)
 * @param coreCoverage            핵심 내용 충족도 원시 점수 (의도 범위 0~5)
 * @param reasoning               근거·판단 과정 원시 점수 (의도 범위 0~4)
 * @param specificity             구체성·실무 연계 원시 점수 (의도 범위 0~3)
 * @param tradeOffsAndExceptions  트레이드오프·예외 원시 점수 (의도 범위 0~3)
 * @param feedback                사용자에게 노출되는 피드백 문자열
 */
public record QuestionEvaluation(
        int turn,
        int score,
        int technicalAccuracy,
        int coreCoverage,
        int reasoning,
        int specificity,
        int tradeOffsAndExceptions,
        String feedback
) {}
