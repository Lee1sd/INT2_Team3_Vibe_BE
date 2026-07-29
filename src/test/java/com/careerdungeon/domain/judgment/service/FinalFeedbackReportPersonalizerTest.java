package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.global.llm.validation.CareerReportValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 서버가 확정한 합격 여부가 최종 리포트 총평에 반영되는지 검증한다. */
class FinalFeedbackReportPersonalizerTest {

    private final CareerReportValidator validator = new CareerReportValidator();

    @Test
    @DisplayName("같은 답변 분석도 합격과 불합격 결과에 따라 총평이 달라진다")
    void appliesServerOutcomeToSummary() {
        String source = validReport();

        String failed = FinalFeedbackReportPersonalizer.apply(source, 79, 80, false);
        String passed = FinalFeedbackReportPersonalizer.apply(source, 80, 80, true);

        assertThat(failed)
                .contains("최종 79점으로 합격 기준 80점에는 미치지 못했습니다.")
                .doesNotContain("합격 기준 80점을 충족했습니다.");
        assertThat(passed)
                .contains("최종 80점으로 합격 기준 80점을 충족했습니다.")
                .doesNotContain("미치지 못했습니다.");
        assertThat(failed).isNotEqualTo(passed);
        assertThatCode(() -> validator.validate(failed)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(passed)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("검증 계층을 거치지 않은 비정형 리포트는 내용 손실 없이 보존한다")
    void preservesUnstructuredReportForDirectDomainCalls() {
        assertThat(FinalFeedbackReportPersonalizer.apply("종합 피드백", 80, 80, true))
                .isEqualTo("종합 피드백");
    }

    /** 결과 문장 치환 후에도 네 섹션 계약을 검증할 수 있는 정상 리포트를 만든다. */
    private String validReport() {
        return CareerReportValidator.appendHypotheticalDisclaimer("""
                🎯 총평
                판단 근거는 좋았지만 운영 검증 설명은 부족했습니다.

                ✨ 이런 점이 매우 훌륭했어요
                - 기술 선택 이유를 설명했습니다.
                - 예외 상황을 구분했습니다.

                🚀 합격을 확정 짓는 2%
                운영 환경에서 검증할 지표를 연결해 보세요.

                💡 Next Step
                ❌ AS-IS (지원자의 기존 답변 방식)
                핵심 방향만 설명했습니다.

                ⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)
                개선 전후를 [예: p95 응답 시간 240ms → 120ms]로 비교해 설명하세요.
                """);
    }
}
