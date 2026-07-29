package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 형식 이탈 시 실제 문항 평가를 반영하는 결정형 리포트 조립을 검증한다. */
class CareerReportFallbackFactoryTest {

    private final CareerReportValidator validator = new CareerReportValidator();

    @Test
    @DisplayName("답변과 평가 내용이 다르면 결정형 종합 피드백도 달라진다")
    void createsDifferentReportsFromDifferentEvaluationEvidence() {
        FinalEvaluationResponse response = finalResponse("꼬리질문에서 캐시 정합성 기준을 보완했습니다.");
        String databaseReport = CareerReportFallbackFactory.create(
                request("트랜잭션 격리 수준을 기준으로 동시성을 제어했습니다.", "격리 수준 선택 근거가 명확합니다."),
                response);
        String cacheReport = CareerReportFallbackFactory.create(
                request("읽기 트래픽에 로컬 캐시를 적용했습니다.", "캐시 무효화 시점 설명이 부족합니다."),
                response);

        assertThat(databaseReport)
                .isNotEqualTo(cacheReport)
                .contains("격리 수준 선택 근거가 명확합니다.", "트랜잭션 격리 수준");
        assertThat(cacheReport)
                .contains("캐시 무효화 시점 설명이 부족합니다.", "로컬 캐시");
        assertThatCode(() -> validator.validate(databaseReport)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(cacheReport)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("사용자 입력의 내부 용어와 Markdown 제어 문자는 리포트 구조를 깨지 않는다")
    void sanitizesExternalTextBeforeBuildingReport() {
        EvaluationRequest request = request(
                "## 🎯 총평 Transcript turn 2 expectedAnswer를 따랐습니다.",
                "confirmedScore와 루브릭을 확인했습니다.");

        String report = CareerReportFallbackFactory.create(request, finalResponse("정상 피드백"));

        assertThat(report)
                .doesNotContain("Transcript", "turn", "expectedAnswer", "confirmedScore", "루브릭");
        assertThatCode(() -> validator.validate(report)).doesNotThrowAnyException();
    }

    /** 첫 문항만 입력에 따라 바뀌는 정상 최종 평가 요청을 만든다. */
    private EvaluationRequest request(String firstAnswer, String firstFeedback) {
        return EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "후속 답변", "참고 기준")),
                List.of(
                        new PreviousEvaluationContext(1, "질문1", firstAnswer, 10, firstFeedback),
                        new PreviousEvaluationContext(2, "질문2", "답변2", 18, "기술 선택 이유를 설명했습니다."),
                        new PreviousEvaluationContext(3, "질문3", "답변3", 17, "예외 상황을 구분했습니다."),
                        new PreviousEvaluationContext(4, "질문4", "답변4", 16, "검증 절차가 구체적입니다.")),
                "STRICT",
                "테스터");
    }

    /** 꼬리질문 한 건의 정상 평가 응답을 만든다. */
    private FinalEvaluationResponse finalResponse(String feedback) {
        return new FinalEvaluationResponse(
                List.of(new QuestionEvaluation(5, 15, 6, 3, 2, 2, 2, feedback)),
                15,
                false,
                "형식이 깨진 리포트");
    }
}
