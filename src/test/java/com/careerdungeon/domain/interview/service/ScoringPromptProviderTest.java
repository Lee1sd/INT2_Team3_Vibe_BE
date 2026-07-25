package com.careerdungeon.domain.interview.service;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringPromptProviderTest {

    private final ScoringPromptProvider sut = new ScoringPromptProvider();

    /** 최초 채점 템플릿이 저장된 모범답안과 고정 루브릭·응답 스키마를 조립하는지 검증한다. */
    @Test
    @DisplayName("최초 채점 프롬프트는 turn 1~4 입력과 v2 루브릭을 조립한다")
    void initialPromptInjectsPairsRubricAndSchema() {
        ScoringPrompt prompt = sut.initialPrompt(EvaluationRequest.initial(
                List.of(
                        pair(1, "질문1", "답변1", "모범답안1"),
                        pair(2, "질문2", "답변2", "모범답안2"),
                        pair(3, "질문3", "답변3", "모범답안3"),
                        pair(4, "질문4", "답변4", "모범답안4")),
                "STRICT",
                "김철수"));

        assertThat(prompt.systemPrompt())
                .contains("technicalAccuracy (0~8)")
                .contains("coreCoverage (0~4)")
                .contains("reasoning (0~3)")
                .contains("specificity (0~3)")
                .contains("tradeOffsAndExceptions (0~2)")
                .contains("새 모범답안을 만들거나 expectedAnswer를 수정하지 마라");
        assertThat(prompt.userPrompt())
                .contains("최초 면접 답변 turn 1, 2, 3, 4")
                .contains("personaTone: STRICT")
                .contains("userName: 김철수")
                .contains("expectedAnswer: 모범답안1")
                .contains("expectedAnswer: 모범답안4")
                .contains("\"weakestQuestionId\"")
                .contains("passed는 false")
                .doesNotContain("{{personaTone}}")
                .doesNotContain("{{questionAnswerPairs}}");
    }

    /** 최종 채점 템플릿이 turn 5와 최초 확정 평가를 서로 다른 용도로 배선하는지 검증한다. */
    @Test
    @DisplayName("최종 채점 프롬프트는 turn 5만 채점하고 이전 평가는 읽기 전용으로 둔다")
    void finalPromptSeparatesTurn5FromPreviousEvaluations() {
        EvaluationRequest request = EvaluationRequest.finalEvaluation(
                List.of(pair(5, "꼬리질문", "꼬리답변", "꼬리 모범답안")),
                List.of(
                        previous(1, 11, "피드백1"),
                        previous(2, 12, "피드백2"),
                        previous(3, 13, "피드백3"),
                        previous(4, 14, "피드백4")),
                "LENIENT",
                "홍길동");

        ScoringPrompt prompt = sut.finalPrompt(request);

        assertThat(prompt.userPrompt())
                .contains("turn 5의 답변 한 건만 채점")
                .contains("expectedAnswer: 꼬리 모범답안")
                .contains("confirmedScore: 11")
                .contains("confirmedFeedback: 피드백4")
                .contains("다시 채점하거나 변경하지 마세요")
                .contains("turn 5 점수 산정에는 사용하지 마세요")
                .contains("🎯 총평")
                .contains("✨ 이런 점이 매우 훌륭했어요")
                .contains("🚀 합격을 확정 짓는 2%")
                .contains("💡 Next Step")
                .contains("❌ AS-IS (지원자의 기존 답변 방식)")
                .contains("⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)")
                .contains("[예: p95 240ms → 120ms]")
                // 가상 수치 고지는 모델에게 요구하지 않는다 — 서버가 항상 덧붙인다(#167).
                .doesNotContain("※ 아래 수치는 답변 구조를 보여주기 위한 가상 예시이며, 실제 측정 결과가 아닙니다.")
                .contains("Transcript에 실제로")
                .contains("실제로 설명한 경우에만")
                .contains("이유를 지어내지 마세요")
                .contains("실제로 측정·달성한 성과로 단정하지 마세요")
                .contains("`expectedAnswer`, `모범답안`")
                .contains("지정된 4개 섹션 외에")
                .contains("\"overallFeedback\"")
                .doesNotContain("{{turn5}}")
                .doesNotContain("{{previousEvaluations}}");
    }

    /** 필수 동적 입력이 비어 있으면 LLM 호출 전에 즉시 거부하는지 검증한다. */
    @Test
    @DisplayName("채점 프롬프트의 빈 모범답안은 거부한다")
    void blankExpectedAnswerThrows() {
        EvaluationRequest request = EvaluationRequest.initial(
                List.of(pair(1, "질문", "답변", " ")),
                "STRICT",
                "홍길동");

        assertThatThrownBy(() -> sut.initialPrompt(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedAnswer");
    }

    /** turn별 채점 입력을 간결하게 생성한다. */
    private QuestionAnswerPair pair(int turn, String question, String answer, String expectedAnswer) {
        return new QuestionAnswerPair(turn, question, answer, expectedAnswer);
    }

    /** 최초 확정 평가 컨텍스트를 간결하게 생성한다. */
    private PreviousEvaluationContext previous(int turn, int score, String feedback) {
        return new PreviousEvaluationContext(turn, "질문" + turn, "답변" + turn, score, feedback);
    }
}
