package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResponseValidatorTest {

    private LlmResponseValidator sut;

    @BeforeEach
    void setUp() {
        sut = new LlmResponseValidator();
    }

    /** 테스트에서 루브릭 세부값은 검증 대상이 아니므로 0으로 고정한다. */
    private static QuestionEvaluation eval(int turn, int score, String feedback) {
        return new QuestionEvaluation(turn, score, 0, 0, 0, 0, 0, feedback);
    }

    // ── QuestionGenerationResponse ──────────────────────────────────────────

    @Nested
    @DisplayName("QuestionGenerationResponse 검증")
    class QuestionGenerationResponseValidation {

        @Test
        @DisplayName("유효한 응답 — 예외 없음")
        void valid_noException() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "질문1", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(3, "질문3", "답3"),
                    new GeneratedQuestion(4, "질문4", "답4")
            ));
            assertThatCode(() -> sut.validate(response)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 응답 → LlmSchemaValidationException")
        void null_response() {
            assertThatThrownBy(() -> sut.validate((QuestionGenerationResponse) null))
                    .isInstanceOf(LlmSchemaValidationException.class);
        }

        @Test
        @DisplayName("questions 빈 리스트 → LlmSchemaValidationException")
        void empty_questions() {
            var response = new QuestionGenerationResponse(List.of());
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("비어 있습니다");
        }

        @Test
        @DisplayName("질문 개수 3개(구 최소값) → LlmSchemaValidationException")
        void wrong_question_count() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "질문1", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(3, "질문3", "답3")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("4개여야");
        }

        @Test
        @DisplayName("turn 범위 이탈(0) → LlmSchemaValidationException")
        void invalid_turn() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(0, "질문1", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(3, "질문3", "답3"),
                    new GeneratedQuestion(4, "질문4", "답4")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("범위를 벗어났습니다");
        }

        @Test
        @DisplayName("turn 5 포함 (FR-03 위반) → LlmSchemaValidationException")
        void turn_out_of_question_range() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "질문1", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(3, "질문3", "답3"),
                    new GeneratedQuestion(5, "질문4", "답4")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("1~4");
        }

        @Test
        @DisplayName("questionText 빈 문자열 → LlmSchemaValidationException")
        void blank_questionText() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "  ", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(3, "질문3", "답3"),
                    new GeneratedQuestion(4, "질문4", "답4")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("질문 텍스트");
        }

        @Test
        @DisplayName("questions 리스트에 null 항목 → LlmSchemaValidationException (NPE 아님)")
        void null_element_in_questions() {
            var questions = new ArrayList<GeneratedQuestion>();
            questions.add(new GeneratedQuestion(1, "질문1", "답1"));
            questions.add(null);
            questions.add(new GeneratedQuestion(3, "질문3", "답3"));
            questions.add(new GeneratedQuestion(4, "질문4", "답4"));
            var response = new QuestionGenerationResponse(questions);
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("null 항목");
        }

        @Test
        @DisplayName("turn 중복 [1,1,2,3] → LlmSchemaValidationException")
        void duplicate_turn() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "질문1", "답1"),
                    new GeneratedQuestion(1, "질문2", "답2"),
                    new GeneratedQuestion(2, "질문3", "답3"),
                    new GeneratedQuestion(3, "질문4", "답4")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("중복");
        }
    }

    // ── FollowUpGenerationResponse ─────────────────────────────────────────

    @Nested
    @DisplayName("FollowUpGenerationResponse 검증")
    class FollowUpGenerationResponseValidation {

        @Test
        @DisplayName("유효한 응답 — 예외 없음")
        void valid_noException() {
            var response = new FollowUpGenerationResponse("꼬리질문", "모범답안");
            assertThatCode(() -> sut.validateFollowUpGeneration(response)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 응답 → LlmSchemaValidationException")
        void null_response() {
            assertThatThrownBy(() -> sut.validateFollowUpGeneration(null))
                    .isInstanceOf(LlmSchemaValidationException.class);
        }

        @Test
        @DisplayName("followUpQuestion 빈 문자열 → LlmSchemaValidationException")
        void blank_followUpQuestion() {
            var response = new FollowUpGenerationResponse(" ", "모범답안");
            assertThatThrownBy(() -> sut.validateFollowUpGeneration(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("followUpQuestion");
        }

        @Test
        @DisplayName("expectedAnswer 빈 문자열 → LlmSchemaValidationException")
        void blank_expectedAnswer() {
            var response = new FollowUpGenerationResponse("꼬리질문", " ");
            assertThatThrownBy(() -> sut.validateFollowUpGeneration(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("expectedAnswer");
        }
    }

    // ── InitialEvaluationResponse: IS-002 최초 채점 ─────────────────────────

    @Nested
    @DisplayName("IS-002 최초 채점 응답 검증 (validateInitialEvaluation)")
    class InitialEvaluationValidation {

        @Test
        @DisplayName("유효한 응답 turn {1,2,3,4} — 예외 없음")
        void valid_noException() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3"),
                    eval(4, 22, "피드백4")
            ), 75, 3, false);
            assertThatCode(() -> sut.validateInitialEvaluation(response)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("루브릭 필드(technicalAccuracy) null → 필드 누락으로 LlmSchemaValidationException")
        void nullRubricField_throws() {
            var response = new InitialEvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, null, 4, 3, 2, 1, "피드백1"),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3")
            ), 53, 2, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("루브릭")
                    .hasMessageContaining("turn=1");
        }

        @Test
        @DisplayName("null 응답 → LlmSchemaValidationException")
        void null_response() {
            assertThatThrownBy(() -> sut.validateInitialEvaluation(null))
                    .isInstanceOf(LlmSchemaValidationException.class);
        }

        @Test
        @DisplayName("totalScore 누락(null) → LlmSchemaValidationException")
        void null_totalScore_throws() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3")
            ), null, 1, false);

            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("totalScore");
        }

        @Test
        @DisplayName("passed 누락(null) → LlmSchemaValidationException")
        void null_passed_throws() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3")
            ), 53, 1, null);

            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("passed");
        }

        @Test
        @DisplayName("evaluations 빈 리스트 → LlmSchemaValidationException")
        void empty_evaluations() {
            var response = new InitialEvaluationResponse(List.of(), 0, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("비어 있습니다");
        }

        @Test
        @DisplayName("feedback 빈 문자열 → LlmSchemaValidationException")
        void blank_feedback() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, ""),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3"),
                    eval(4, 22, "피드백4")
            ), 75, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("피드백");
        }

        @Test
        @DisplayName("weakestQuestionId 범위 이탈(6) → LlmSchemaValidationException")
        void invalid_weakestQuestionId() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3"),
                    eval(4, 22, "피드백4")
            ), 75, 6, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("weakestQuestionId");
        }

        @Test
        @DisplayName("weakestQuestionId가 evaluations turn 목록에 없음(5) → LlmSchemaValidationException")
        void weakestQuestionId_not_in_evaluation_turns() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3"),
                    eval(4, 22, "피드백4")
            ), 75, 5, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("evaluations의 turn 목록에 없습니다");
        }

        @Test
        @DisplayName("evaluations 리스트에 null 항목 → LlmSchemaValidationException (NPE 아님)")
        void null_element_in_evaluations() {
            var evals = new ArrayList<QuestionEvaluation>();
            evals.add(eval(1, 18, "피드백"));
            evals.add(null);
            var response = new InitialEvaluationResponse(evals, 18, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("null 항목");
        }

        @Test
        @DisplayName("evaluations turn 중복 → LlmSchemaValidationException")
        void duplicate_evaluation_turn() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(1, 20, "피드백2")
            ), 38, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("중복");
        }

        @Test
        @DisplayName("turn {1,2} 누락 — {1,2,3} 아닌 구성 → LlmSchemaValidationException")
        void wrong_turn_composition_missing() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(2, 20, "피드백2")
            ), 38, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("올바르지 않습니다");
        }

        @Test
        @DisplayName("turn {1,2,3,4,5} 초과 — {1,2,3,4} 아닌 구성 → LlmSchemaValidationException")
        void wrong_turn_composition_extra() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 18, "피드백1"),
                    eval(2, 20, "피드백2"),
                    eval(3, 15, "피드백3"),
                    eval(4, 22, "피드백4"),
                    eval(5, 20, "피드백5")
            ), 95, 3, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("올바르지 않습니다");
        }

        @Test
        @DisplayName("score 범위 이탈값도 clamp 없이 통과 — clamp는 ③의 책임")
        void out_of_range_score_passes_validation() {
            var response = new InitialEvaluationResponse(List.of(
                    eval(1, 99, "피드백1"),
                    eval(2, 99, "피드백2"),
                    eval(3, 99, "피드백3"),
                    eval(4, 99, "피드백4")
            ), 396, 1, false);
            assertThatCode(() -> sut.validateInitialEvaluation(response)).doesNotThrowAnyException();
        }
    }

    // ── FinalEvaluationResponse: IS-002b 꼬리질문 최종 응답 ─────────────────

    @Nested
    @DisplayName("IS-002b 꼬리질문 최종 응답 검증 (validateFinalEvaluation)")
    class FinalEvaluationValidation {

        @Test
        @DisplayName("루브릭 필드(tradeOffsAndExceptions) null → 필드 누락으로 LlmSchemaValidationException")
        void nullRubricField_throws() {
            var response = new FinalEvaluationResponse(List.of(
                    new QuestionEvaluation(5, 22, 8, 4, 3, 2, null, "꼬리질문 피드백")
            ), 22, false, "종합 피드백");
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("루브릭")
                    .hasMessageContaining("turn=5");
        }

        @Test
        @DisplayName("유효한 응답 turn {5}, overallFeedback 존재 → 통과")
        void finalEvaluation_turn5_passes() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(5, 22, "꼬리질문 피드백")
            ), 22, false, CareerReportValidatorTest.validReport());
            assertThatCode(() -> sut.validateFinalEvaluation(response)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("꼬리질문(turn=5) feedback 없음 → LlmSchemaValidationException")
        void followUpTurn_blank_feedback_throws() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(5, 22, "")
            ), 22, false, "종합 피드백");
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("꼬리질문");
        }

        @Test
        @DisplayName("이전 문항만 반환하고 꼬리질문 turn 5가 없으면 거부한다")
        void followUpTurn_missing_from_response() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(1, 20, ""),
                    eval(2, 25, ""),
                    eval(3, 18, "")
            ), 63, false, "종합 피드백");
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("올바르지 않습니다");
        }

        @Test
        @DisplayName("null 응답 → LlmSchemaValidationException")
        void null_response() {
            assertThatThrownBy(() -> sut.validateFinalEvaluation(null))
                    .isInstanceOf(LlmSchemaValidationException.class);
        }

        @Test
        @DisplayName("totalScore 누락(null) → LlmSchemaValidationException")
        void null_totalScore_throws() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(5, 22, "꼬리질문 피드백")
            ), null, false, "종합 피드백");

            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("totalScore");
        }

        @Test
        @DisplayName("passed 누락(null) → LlmSchemaValidationException")
        void null_passed_throws() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(5, 22, "꼬리질문 피드백")
            ), 22, null, "종합 피드백");

            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("passed");
        }

        @Test
        @DisplayName("evaluations turn 중복 → LlmSchemaValidationException")
        void duplicate_turn() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(5, 22, "피드백"),
                    eval(5, 18, "피드백2")
            ), 40, false, "종합 피드백");
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("중복");
        }

        @Test
        @DisplayName("꼬리질문 turn 외 이전 문항이 섞이면 거부한다")
        void finalEvaluation_previousTurnIncluded_throws() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(1, 20, "이전 피드백"),
                    eval(5, 22, "꼬리질문 피드백")
            ), 42, false, "종합 피드백");
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("올바르지 않습니다");
        }

        @Test
        @DisplayName("overallFeedback 빈 문자열 → LlmSchemaValidationException")
        void blank_overallFeedback_throws() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(5, 22, "꼬리질문 피드백")
            ), 22, false, "");
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("overallFeedback");
        }

        @Test
        @DisplayName("형식이 없는 일반 문장형 overallFeedback은 거부한다")
        void unstructuredOverallFeedbackThrows() {
            var response = new FinalEvaluationResponse(List.of(
                    eval(5, 20, "꼬리질문 피드백")
            ), 20, false, "전반적으로 잘 답변했지만 정량 근거가 부족했습니다.");

            assertThatThrownBy(() -> sut.validateFinalEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("섹션");
        }
    }
}
