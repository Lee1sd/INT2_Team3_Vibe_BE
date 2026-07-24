package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 최종 리포트 생성에 전달되는 최초 1~4번 확정 평가 컨텍스트의 공통 계약을 검증한다.
 */
public final class PreviousEvaluationContextValidator {

    private static final Set<Integer> REQUIRED_TURNS = Set.of(1, 2, 3, 4);
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 20;

    private PreviousEvaluationContextValidator() {
    }

    /** 네 건의 turn·필수 문자열·서버 확정 점수 범위를 검증한다. */
    public static void validate(List<PreviousEvaluationContext> contexts) {
        if (contexts == null || contexts.size() != REQUIRED_TURNS.size()
                || contexts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "이전 평가 컨텍스트는 turn 1~4 네 건이어야 합니다.");
        }

        Set<Integer> turns = contexts.stream()
                .map(PreviousEvaluationContext::turn)
                .collect(Collectors.toSet());
        if (!turns.equals(REQUIRED_TURNS)) {
            throw new IllegalArgumentException(
                    "이전 평가 컨텍스트 turn은 1,2,3,4이어야 합니다: " + turns);
        }

        for (PreviousEvaluationContext context : contexts) {
            if (isBlank(context.questionText()) || isBlank(context.userAnswer())
                    || isBlank(context.feedback())
                    || context.score() < MIN_SCORE || context.score() > MAX_SCORE) {
                throw new IllegalArgumentException(
                        "이전 평가 컨텍스트의 질문, 답변, 점수, 피드백이 올바르지 않습니다: turn="
                                + context.turn());
            }
        }
    }

    /** 공통 컨텍스트의 필수 문자열 누락 여부를 판별한다. */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
