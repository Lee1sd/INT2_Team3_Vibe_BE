package com.careerdungeon.global.llm.dto;

/**
 * 문항 하나에 대한 LLM 평가 결과.
 *
 * <p>5개 루브릭 필드는 {@code Integer}(boxed)다 — LLM 응답에서 필드 자체가 누락되면
 * {@code null}로 역직렬화되어 {@link com.careerdungeon.global.llm.validation.LlmResponseValidator}가
 * 이를 검출할 수 있어야 한다(이슈 #6에서 {@code weakestQuestionId} sentinel 문제를 겪은 뒤
 * 같은 실수를 반복하지 않기 위함, ADR-010).
 *
 * @param turn                    질문 순서 (1~4)
 * @param score                   LLM이 반환한 원시 합산 점수 (0~25 의도, 범위 이탈 가능) — clamp는 ③의 책임
 * @param technicalAccuracy       기술적 정확성 원시 점수 (의도 범위 0~10). null이면 필드 누락으로 검증 실패
 * @param coreCoverage            핵심 내용 충족도 원시 점수 (의도 범위 0~5). null이면 필드 누락으로 검증 실패
 * @param reasoning               근거·판단 과정 원시 점수 (의도 범위 0~4). null이면 필드 누락으로 검증 실패
 * @param specificity             구체성·실무 연계 원시 점수 (의도 범위 0~3). null이면 필드 누락으로 검증 실패
 * @param tradeOffsAndExceptions  트레이드오프·예외 원시 점수 (의도 범위 0~3). null이면 필드 누락으로 검증 실패
 * @param feedback                사용자에게 노출되는 피드백 문자열
 */
public record QuestionEvaluation(
        int turn,
        int score,
        Integer technicalAccuracy,
        Integer coreCoverage,
        Integer reasoning,
        Integer specificity,
        Integer tradeOffsAndExceptions,
        String feedback
) {}
