package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
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
                    new GeneratedQuestion(3, "질문3", "답3")
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
        @DisplayName("질문 개수 3개 미만 → LlmSchemaValidationException")
        void wrong_question_count() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "질문1", "답1")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("3개여야");
        }

        @Test
        @DisplayName("turn 범위 이탈(0) → LlmSchemaValidationException")
        void invalid_turn() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(0, "질문1", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(3, "질문3", "답3")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("범위를 벗어났습니다");
        }

        @Test
        @DisplayName("turn 4 포함 (FR-03 위반) → LlmSchemaValidationException")
        void turn_out_of_question_range() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "질문1", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(4, "질문3", "답3")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("1~3");
        }

        @Test
        @DisplayName("questionText 빈 문자열 → LlmSchemaValidationException")
        void blank_questionText() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "  ", "답1"),
                    new GeneratedQuestion(2, "질문2", "답2"),
                    new GeneratedQuestion(3, "질문3", "답3")
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
            var response = new QuestionGenerationResponse(questions);
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("null 항목");
        }

        @Test
        @DisplayName("turn 중복 [1,1,2] → LlmSchemaValidationException")
        void duplicate_turn() {
            var response = new QuestionGenerationResponse(List.of(
                    new GeneratedQuestion(1, "질문1", "답1"),
                    new GeneratedQuestion(1, "질문2", "답2"),
                    new GeneratedQuestion(2, "질문3", "답3")
            ));
            assertThatThrownBy(() -> sut.validate(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("중복");
        }
    }

    // ── EvaluationResponse: IS-002 최초 채점 ────────────────────────────────

    @Nested
    @DisplayName("IS-002 최초 채점 응답 검증 (validateInitialEvaluation)")
    class InitialEvaluationValidation {

        @Test
        @DisplayName("유효한 응답 turn {1,2,3} — 예외 없음")
        void valid_noException() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, "피드백1"),
                    new QuestionEvaluation(2, 20, "피드백2"),
                    new QuestionEvaluation(3, 15, "피드백3")
            ), 53, 3, false);
            assertThatCode(() -> sut.validateInitialEvaluation(response)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 응답 → LlmSchemaValidationException")
        void null_response() {
            assertThatThrownBy(() -> sut.validateInitialEvaluation(null))
                    .isInstanceOf(LlmSchemaValidationException.class);
        }

        @Test
        @DisplayName("evaluations 빈 리스트 → LlmSchemaValidationException")
        void empty_evaluations() {
            var response = new EvaluationResponse(List.of(), 0, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("비어 있습니다");
        }

        @Test
        @DisplayName("feedback 빈 문자열 → LlmSchemaValidationException")
        void blank_feedback() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, ""),
                    new QuestionEvaluation(2, 20, "피드백2"),
                    new QuestionEvaluation(3, 15, "피드백3")
            ), 53, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("피드백");
        }

        @Test
        @DisplayName("weakestQuestionId 범위 이탈(5) → LlmSchemaValidationException")
        void invalid_weakestQuestionId() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, "피드백1"),
                    new QuestionEvaluation(2, 20, "피드백2"),
                    new QuestionEvaluation(3, 15, "피드백3")
            ), 53, 5, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("weakestQuestionId");
        }

        @Test
        @DisplayName("weakestQuestionId가 evaluations turn 목록에 없음(4) → LlmSchemaValidationException")
        void weakestQuestionId_not_in_evaluation_turns() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, "피드백1"),
                    new QuestionEvaluation(2, 20, "피드백2"),
                    new QuestionEvaluation(3, 15, "피드백3")
            ), 53, 4, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("evaluations의 turn 목록에 없습니다");
        }

        @Test
        @DisplayName("evaluations 리스트에 null 항목 → LlmSchemaValidationException (NPE 아님)")
        void null_element_in_evaluations() {
            var evals = new ArrayList<QuestionEvaluation>();
            evals.add(new QuestionEvaluation(1, 18, "피드백"));
            evals.add(null);
            var response = new EvaluationResponse(evals, 18, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("null 항목");
        }

        @Test
        @DisplayName("evaluations turn 중복 → LlmSchemaValidationException")
        void duplicate_evaluation_turn() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, "피드백1"),
                    new QuestionEvaluation(1, 20, "피드백2")
            ), 38, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("중복");
        }

        @Test
        @DisplayName("turn {1,2} 누락 — {1,2,3} 아닌 구성 → LlmSchemaValidationException")
        void wrong_turn_composition_missing() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, "피드백1"),
                    new QuestionEvaluation(2, 20, "피드백2")
            ), 38, 1, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("올바르지 않습니다");
        }

        @Test
        @DisplayName("turn {1,2,3,4} 초과 — {1,2,3} 아닌 구성 → LlmSchemaValidationException")
        void wrong_turn_composition_extra() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 18, "피드백1"),
                    new QuestionEvaluation(2, 20, "피드백2"),
                    new QuestionEvaluation(3, 15, "피드백3"),
                    new QuestionEvaluation(4, 22, "피드백4")
            ), 75, 3, false);
            assertThatThrownBy(() -> sut.validateInitialEvaluation(response))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("올바르지 않습니다");
        }

        @Test
        @DisplayName("score 범위 이탈값도 clamp 없이 통과 — clamp는 ③의 책임")
        void out_of_range_score_passes_validation() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 99, "피드백1"),
                    new QuestionEvaluation(2, 99, "피드백2"),
                    new QuestionEvaluation(3, 99, "피드백3")
            ), 297, 1, false);
            assertThatCode(() -> sut.validateInitialEvaluation(response)).doesNotThrowAnyException();
        }
    }

    // ── EvaluationResponse: IS-002b 꼬리질문 최종 응답 ──────────────────────

    @Nested
    @DisplayName("IS-002b 꼬리질문 최종 응답 검증 (validateFinalEvaluation)")
    class FinalEvaluationValidation {

        @Test
        @DisplayName("IS-002b 최종 응답 turn {1,2,4} — weakestQuestionId 없음(0) → 통과")
        void finalEvaluation_turns124_passes() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 20, ""),
                    new QuestionEvaluation(2, 25, ""),
                    new QuestionEvaluation(4, 22, "꼬리질문 피드백")
            ), 67, 0, false);
            assertThatCode(() -> sut.validateFinalEvaluation(response, 4))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("꼬리질문 turn feedback 없음 → LlmSchemaValidationException")
        void followUpTurn_blank_feedback_throws() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 20, ""),
                    new QuestionEvaluation(2, 25, ""),
                    new QuestionEvaluation(4, 22, "")
            ), 67, 0, false);
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response, 4))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("꼬리질문");
        }

        @Test
        @DisplayName("꼬리질문 turn이 응답에 없음(3개지만 turn 4 미포함) → LlmSchemaValidationException")
        void followUpTurn_missing_from_response() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(1, 20, ""),
                    new QuestionEvaluation(2, 25, ""),
                    new QuestionEvaluation(3, 18, "")
            ), 63, 0, false);
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response, 4))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("꼬리질문 turn=4");
        }

        @Test
        @DisplayName("null 응답 → LlmSchemaValidationException")
        void null_response() {
            assertThatThrownBy(() -> sut.validateFinalEvaluation(null, 4))
                    .isInstanceOf(LlmSchemaValidationException.class);
        }

        @Test
        @DisplayName("evaluations turn 중복 → LlmSchemaValidationException")
        void duplicate_turn() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(4, 22, "피드백"),
                    new QuestionEvaluation(4, 18, "피드백2")
            ), 40, 0, false);
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response, 4))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("중복");
        }

        @Test
        @DisplayName("꼬리질문 turn만 있고 이전 문항 누락({4}만 응답) → LlmSchemaValidationException")
        void finalEvaluation_only_followUpTurn_throws() {
            var response = new EvaluationResponse(List.of(
                    new QuestionEvaluation(4, 22, "꼬리질문 피드백")
            ), 22, 0, false);
            assertThatThrownBy(() -> sut.validateFinalEvaluation(response, 4))
                    .isInstanceOf(LlmSchemaValidationException.class)
                    .hasMessageContaining("개수");
        }
    }
}
