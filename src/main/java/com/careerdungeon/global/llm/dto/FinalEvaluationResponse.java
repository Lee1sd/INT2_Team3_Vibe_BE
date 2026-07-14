package com.careerdungeon.global.llm.dto;

import java.util.List;

/**
 * IS-002b 꼬리질문 최종 채점 응답 — ③(최용성)의 채점 루틴이 소비하는 JSON 스키마.
 *
 * <p>필드 계약 (③과 합의된 SSOT, 이슈 #6/#12 논의 결과 ADR-008로 확정):
 * <ul>
 *   <li>{@code evaluations} — 문항별 평가 목록 (turn 1~4 전체 — 최초 3문항 + 꼬리질문)</li>
 *   <li>{@code totalScore} — 원시 합산 점수 (clamp 전). clamp는 ③의 책임</li>
 *   <li>{@code passed} — LLM의 합격 판정. 최종 판정은 clamp 후 ③에서 재계산</li>
 *   <li>{@code overallFeedback} — 면접 전체에 대한 종합 피드백 문자열</li>
 * </ul>
 *
 * <p>{@code weakestQuestionId}는 계약상 존재하지 않는다 — 이슈 #6에서 지적된 "stale
 * weakestQuestionId가 IS-002b 응답에 실려 다운스트림으로 새는 문제"를 타입 분리로
 * 원천 차단한다.
 */
public record FinalEvaluationResponse(
        List<QuestionEvaluation> evaluations,
        int totalScore,
        boolean passed,
        String overallFeedback
) {}
