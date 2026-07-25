package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CareerReportValidatorTest {

    private final CareerReportValidator sut = new CareerReportValidator();

    @Test
    @DisplayName("네 섹션·강점 두 개·정량 예시 계약을 지킨 리포트는 통과한다")
    void validReportPasses() {
        assertThatCode(() -> sut.validate(validReport())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("필수 섹션 순서가 바뀌면 거부한다")
    void wrongSectionOrderThrows() {
        String report = validReport()
                .replace(
                        "✨ 이런 점이 매우 훌륭했어요",
                        "임시 제목")
                .replace(
                        "🚀 합격을 확정 짓는 2%",
                        "✨ 이런 점이 매우 훌륭했어요")
                .replace(
                        "임시 제목",
                        "🚀 합격을 확정 짓는 2%");

        assertThatThrownBy(() -> sut.validate(report))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("순서");
    }

    @Test
    @DisplayName("총평이 3줄이면 형식 오류로 거부한다")
    void summaryLongerThanTwoLinesThrows() {
        String report = validReport().replace(
                "트레이드오프를 고려했지만 운영 지표로 증명하는 설명은 부족했습니다.",
                "트레이드오프를 고려했습니다.\n"
                        + "구조적 판단도 설명했습니다.\n"
                        + "다만 운영 지표로 증명하는 설명은 부족했습니다.");

        assertThatThrownBy(() -> sut.validate(report))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("1~2줄");
    }

    @Test
    @DisplayName("Strengths 불릿이 정확히 두 개가 아니면 거부한다")
    void wrongStrengthBulletCountThrows() {
        String report = validReport().replace("- 꼬리질문에서 정합성 보완 전략을 연결했습니다.\n", "");

        assertThatThrownBy(() -> sut.validate(report))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("불릿 두 개");
    }

    @Test
    @DisplayName("모델 응답에 가상 수치 안내 문구가 아예 없어도 통과한다 — 서버가 항상 덧붙이므로 모델에게 요구하지 않는다")
    void missingDisclaimerStillPasses() {
        assertThatCode(() -> sut.validate(validReport())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("appendHypotheticalDisclaimer는 리포트 끝에 가상 수치 안내를 항상 덧붙인다")
    void appendHypotheticalDisclaimerAlwaysAddsDisclaimerAtEnd() {
        String report = validReport();
        String withDisclaimer = CareerReportValidator.appendHypotheticalDisclaimer(report);

        assertThat(withDisclaimer.stripTrailing())
                .startsWith(report.stripTrailing())
                .endsWith(CareerReportValidator.HYPOTHETICAL_DISCLAIMER);
    }

    @Test
    @DisplayName("모델이 고지 문구를 본문 중간에 이미 썼어도 appendHypotheticalDisclaimer는 끝에 정확히 한 번만 남긴다(리뷰 지적)")
    void appendHypotheticalDisclaimerRemovesExistingOccurrenceBeforeAppending() {
        String reportWithEmbeddedDisclaimer = validReport().replace(
                "적용 전후를",
                CareerReportValidator.HYPOTHETICAL_DISCLAIMER + "\n적용 전후를");

        String result = CareerReportValidator.appendHypotheticalDisclaimer(reportWithEmbeddedDisclaimer);

        int firstIndex = result.indexOf(CareerReportValidator.HYPOTHETICAL_DISCLAIMER);
        int lastIndex = result.lastIndexOf(CareerReportValidator.HYPOTHETICAL_DISCLAIMER);
        assertThat(firstIndex).isEqualTo(lastIndex).isNotNegative();
        assertThat(result.stripTrailing()).endsWith(CareerReportValidator.HYPOTHETICAL_DISCLAIMER);
    }

    @Test
    @DisplayName("appendHypotheticalDisclaimer는 멱등하다 — 모델 응답에 고지가 이미 있어도 중복 첨부하지 않는다")
    void appendHypotheticalDisclaimerIsIdempotent() {
        String reportWithDisclaimerAlready =
                validReport().stripTrailing() + "\n\n" + CareerReportValidator.HYPOTHETICAL_DISCLAIMER;

        String result = CareerReportValidator.appendHypotheticalDisclaimer(reportWithDisclaimerAlready);

        assertThat(countOccurrences(result, CareerReportValidator.HYPOTHETICAL_DISCLAIMER)).isEqualTo(1);
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    @Test
    @DisplayName("TO-BE의 숫자가 예시 표지 밖에 있으면 거부한다")
    void unmarkedHypotheticalNumberThrows() {
        String report = validReport().replace(
                "[예: p95 응답 시간 320ms → 140ms]",
                "p95 응답 시간 320ms → 140ms");

        assertThatThrownBy(() -> sut.validate(report))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("모든 가상 수치");
    }

    @Test
    @DisplayName("사용자 리포트에 내부 처리 용어가 노출되면 거부한다")
    void prohibitedInternalTermThrows() {
        String report = validReport().replace(
                "캐시 무효화",
                "turn 2의 캐시 무효화");

        assertThatThrownBy(() -> sut.validate(report))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("내부 처리 용어");
    }

    @Test
    @DisplayName("지정되지 않은 Markdown 제목이 추가되면 거부한다")
    void additionalMarkdownHeadingThrows() {
        String report = validReport() + "\n## 결론\n추가 설명";

        assertThatThrownBy(() -> sut.validate(report))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("추가 섹션");
    }

    /** 모든 테스트가 공유하는 정상 최종 커리어 리포트를 반환한다. */
    static String validReport() {
        return """
                🎯 총평
                트레이드오프를 고려했지만 운영 지표로 증명하는 설명은 부족했습니다.

                ✨ 이런 점이 매우 훌륭했어요
                - JOIN FETCH의 적용 범위를 구분한 판단이 좋았습니다.
                - 꼬리질문에서 정합성 보완 전략을 연결했습니다.

                🚀 합격을 확정 짓는 2%
                캐시 무효화 전략을 운영 데이터와 연결해 설명하세요.

                💡 Next Step
                ❌ AS-IS (지원자의 기존 답변 방식)
                캐시를 삭제해 정합성을 맞췄습니다.

                ⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)
                적용 전후를 [예: p95 응답 시간 320ms → 140ms]와 [예: 캐시 적중률 72% → 85%]로 비교하세요.
                """;
    }
}
