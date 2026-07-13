package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final Set<Integer> INITIAL_EVAL_TURNS = Set.of(1, 2, 3);
    private static final Set<Integer> FINAL_EVAL_TURNS = Set.of(1, 2, 3, 4);
    private static final int FOLLOW_UP_TURN = 4;

    // ── QuestionGenerationResponse ──────────────────────────────────────────

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

    // ── InitialEvaluationResponse / FinalEvaluationResponse ──────────────────

    /**
     * IS-002 최초 채점 응답 검증.
     * turn 구성이 정확히 {1,2,3}이어야 하고, 전 문항 feedback 필수, weakestQuestionId 유효.
     */
    public void validateInitialEvaluation(InitialEvaluationResponse response) {
        if (response == null) {
            throw new LlmSchemaValidationException("InitialEvaluationResponse가 null입니다.");
        }
        Set<Integer> seenTurns = validateEvaluationCore(response.evaluations());
        if (!seenTurns.equals(INITIAL_EVAL_TURNS)) {
            throw new LlmSchemaValidationException(
                    "최초 채점 응답 turn 구성이 올바르지 않습니다: " + seenTurns
                    + " (기대: " + INITIAL_EVAL_TURNS + ")");
        }
        for (QuestionEvaluation e : response.evaluations()) {
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
     * IS-002b 꼬리질문 최종 응답 검증 (api-spec.md IS-002b).
     * seenTurns가 turn {1,2,3,4} 전체와 정확히 일치해야 한다(ADR-010 — 최초 3문항 +
     * 꼬리질문을 합친 4개 전체를 다시 채점).
     * weakestQuestionId는 타입 계약상 존재하지 않으므로 검증하지 않는다(이슈 #6, ADR-008).
     * 꼬리질문 turn만 feedback 필수, 이전 문항은 feedback 없어도 정상.
     */
    public void validateFinalEvaluation(FinalEvaluationResponse response) {
        if (response == null) {
            throw new LlmSchemaValidationException("FinalEvaluationResponse가 null입니다.");
        }
        Set<Integer> seenTurns = validateEvaluationCore(response.evaluations());
        if (!seenTurns.equals(FINAL_EVAL_TURNS)) {
            throw new LlmSchemaValidationException(
                    "최종 채점 응답 turn 구성이 올바르지 않습니다: " + seenTurns
                    + " (기대: " + FINAL_EVAL_TURNS + ")");
        }
        for (QuestionEvaluation e : response.evaluations()) {
            if (e.turn() == FOLLOW_UP_TURN && isBlank(e.feedback())) {
                throw new LlmSchemaValidationException(
                        "꼬리질문 turn=" + FOLLOW_UP_TURN + " 피드백이 비어 있습니다.");
            }
        }
        if (isBlank(response.overallFeedback())) {
            throw new LlmSchemaValidationException("overallFeedback이 비어 있습니다.");
        }
    }

    /** 구조 검증 공통 — null/empty/null요소/turn범위/중복/루브릭 필드 체크. weakestQuestionId는 호출자가 판단. */
    private Set<Integer> validateEvaluationCore(List<QuestionEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            throw new LlmSchemaValidationException("evaluations 필드가 null이거나 비어 있습니다.");
        }
        Set<Integer> seenTurns = new HashSet<>();
        for (QuestionEvaluation e : evaluations) {
            if (e == null) {
                throw new LlmSchemaValidationException("evaluations 리스트에 null 항목이 있습니다.");
            }
            validateTurn(e.turn(), "evaluations[].turn");
            if (!seenTurns.add(e.turn())) {
                throw new LlmSchemaValidationException(
                        "evaluations[].turn에 중복값이 있습니다: turn=" + e.turn());
            }
            validateRubricScores(e);
        }
        return seenTurns;
    }

    /**
     * 5개 루브릭 필드가 하나라도 null이면 LLM 응답에서 해당 필드가 누락된 것으로 판단해
     * 검증 실패시킨다(이슈 #6 weakestQuestionId sentinel 문제 재발 방지, ADR-010).
     */
    private void validateRubricScores(QuestionEvaluation e) {
        if (e.technicalAccuracy() == null || e.coreCoverage() == null || e.reasoning() == null
                || e.specificity() == null || e.tradeOffsAndExceptions() == null) {
            throw new LlmSchemaValidationException(
                    "turn=" + e.turn() + " 루브릭 점수 필드가 누락되었습니다"
                            + " (technicalAccuracy/coreCoverage/reasoning/specificity/tradeOffsAndExceptions).");
        }
    }

    // ── private helpers ─────────────────────────────────────────────────────

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
