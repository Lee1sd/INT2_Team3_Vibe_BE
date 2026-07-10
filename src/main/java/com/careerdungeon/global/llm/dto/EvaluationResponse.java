package com.careerdungeon.global.llm.dto;

import java.util.List;

/**
 * LLM이 반환하는 채점 결과 — ③(최용성)의 채점 루틴이 소비하는 JSON 스키마.
 *
 * <p>필드 계약 (③과 합의된 SSOT):
 * <ul>
 *   <li>{@code evaluations} — 문항별 평가 목록 (turn·score·feedback)</li>
 *   <li>{@code totalScore} — 원시 합산 점수 (clamp 전, 0~100 의도). clamp는 ③의 책임</li>
 *   <li>{@code weakestQuestionId} — 가장 낮은 점수를 받은 문항의 turn 번호</li>
 *   <li>{@code passed} — LLM의 합격 판정. 최종 판정은 clamp 후 ③에서 재계산</li>
 * </ul>
 */
public record EvaluationResponse(
        List<QuestionEvaluation> evaluations,
        int totalScore,
        int weakestQuestionId,
        boolean passed
) {}
