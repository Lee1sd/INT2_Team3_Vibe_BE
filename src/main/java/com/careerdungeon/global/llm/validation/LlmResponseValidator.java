package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM 응답 DTO의 필드 계약을 검증한다.
 *
 * <p>검증 실패 시 {@link LlmSchemaValidationException}을 던진다.
 * 호출 측({@code LlmInvocationService})에서 {@code @Retryable}로 최대 2회 재요청한다.
 *
 * <p>점수 범위(0~25, 0~100) clamp는 여기서 하지 않는다 — ③(judgment 도메인)의 책임이다.
 */
@Component
public class LlmResponseValidator {

    private static final int MIN_TURN = 1;
    private static final int MAX_QUESTION_TURN = 3;  // FR-03/IS-001: 질문은 turn 1~3만 유효
    private static final int MAX_EVAL_TURN = 4;       // 채점은 꼬리질문 포함 turn 1~4
    private static final int EXPECTED_QUESTION_COUNT = 3;

    public void validate(QuestionGenerationResponse response) {
        if (response == null) {
            throw new LlmSchemaValidationException("QuestionGenerationResponse가 null입니다.");
        }
        if (response.questions() == null || response.questions().isEmpty()) {
            throw new LlmSchemaValidationException("questions 필드가 null이거나 비어 있습니다.");
        }
        if (response.questions().size() != EXPECTED_QUESTION_COUNT) {
            throw new LlmSchemaValidationException(
                    "questions 개수가 " + EXPECTED_QUESTION_COUNT + "개여야 하지만 "
                    + response.questions().size() + "개입니다.");
        }
        Set<Integer> seenTurns = new HashSet<>();
        for (GeneratedQuestion q : response.questions()) {
            if (q == null) {
                throw new LlmSchemaValidationException("questions 리스트에 null 항목이 있습니다.");
            }
            validateQuestionTurn(q.turn(), "questions[].turn");
            if (!seenTurns.add(q.turn())) {
                throw new LlmSchemaValidationException(
                        "questions[].turn에 중복값이 있습니다: turn=" + q.turn());
            }
            if (isBlank(q.questionText())) {
                throw new LlmSchemaValidationException(
                        "turn=" + q.turn() + " 질문 텍스트가 비어 있습니다.");
            }
            if (isBlank(q.expectedAnswer())) {
                throw new LlmSchemaValidationException(
                        "turn=" + q.turn() + " 모범답변이 비어 있습니다.");
            }
        }
    }

    public void validate(EvaluationResponse response) {
        if (response == null) {
            throw new LlmSchemaValidationException("EvaluationResponse가 null입니다.");
        }
        if (response.evaluations() == null || response.evaluations().isEmpty()) {
            throw new LlmSchemaValidationException("evaluations 필드가 null이거나 비어 있습니다.");
        }
        Set<Integer> seenTurns = new HashSet<>();
        for (QuestionEvaluation e : response.evaluations()) {
            if (e == null) {
                throw new LlmSchemaValidationException("evaluations 리스트에 null 항목이 있습니다.");
            }
            validateTurn(e.turn(), "evaluations[].turn");
            if (!seenTurns.add(e.turn())) {
                throw new LlmSchemaValidationException(
                        "evaluations[].turn에 중복값이 있습니다: turn=" + e.turn());
            }
            if (isBlank(e.feedback())) {
                throw new LlmSchemaValidationException(
                        "turn=" + e.turn() + " 피드백이 비어 있습니다.");
            }
        }
        validateTurn(response.weakestQuestionId(), "weakestQuestionId");
        if (!seenTurns.contains(response.weakestQuestionId())) {
            throw new LlmSchemaValidationException(
                    "weakestQuestionId=" + response.weakestQuestionId()
                    + "가 evaluations의 turn 목록에 없습니다.");
        }
    }

    /**
     * 채점 응답에 요청 turn이 전부 포함됐는지 확인한다.
     * {@link com.careerdungeon.global.llm.LlmInvocationService}가 request의 turns를 추출해 전달한다.
     */
    public void validate(EvaluationResponse response, Set<Integer> expectedTurns) {
        validate(response);
        if (expectedTurns == null || expectedTurns.isEmpty()) {
            return;
        }
        Set<Integer> actualTurns = response.evaluations().stream()
                .map(QuestionEvaluation::turn)
                .collect(Collectors.toSet());
        for (int expected : expectedTurns) {
            if (!actualTurns.contains(expected)) {
                throw new LlmSchemaValidationException(
                        "evaluations에 turn=" + expected + " 채점 결과가 누락됐습니다.");
            }
        }
    }

    private void validateQuestionTurn(int turn, String fieldName) {
        if (turn < MIN_TURN || turn > MAX_QUESTION_TURN) {
            throw new LlmSchemaValidationException(
                    fieldName + " 값이 범위를 벗어났습니다: " + turn + " (허용: 1~3)");
        }
    }

    private void validateTurn(int turn, String fieldName) {
        if (turn < MIN_TURN || turn > MAX_EVAL_TURN) {
            throw new LlmSchemaValidationException(
                    fieldName + " 값이 범위를 벗어났습니다: " + turn + " (허용: 1~4)");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
