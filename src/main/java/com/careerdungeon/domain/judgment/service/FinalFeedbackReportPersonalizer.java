package com.careerdungeon.domain.judgment.service;

/**
 * LLM 리포트에 서버가 확정한 최종 점수와 합격 여부를 반영한다.
 *
 * <p>LLM 호출 시점에는 레벨별 통과 기준을 알 수 없으므로, judgment가 점수를 합산한 뒤
 * 총평 첫 줄만 결과 문장으로 교체한다. 나머지 답변별 분석은 그대로 보존한다.
 */
final class FinalFeedbackReportPersonalizer {

    private static final String SUMMARY_HEADING = "🎯 총평";
    private static final String STRENGTHS_HEADING = "✨ 이런 점이 매우 훌륭했어요";

    private FinalFeedbackReportPersonalizer() {
    }

    /**
     * 서버 최종 판정 문장과 기존 답변 분석 총평을 최대 두 줄로 조립한다.
     */
    static String apply(
            String report,
            int totalScore,
            int passingScore,
            boolean passed) {
        int summaryStart = report.indexOf(SUMMARY_HEADING);
        int strengthsStart = report.indexOf(STRENGTHS_HEADING);
        if (summaryStart < 0 || strengthsStart <= summaryStart) {
            // 직접 서비스 테스트처럼 리포트 검증 계층을 거치지 않은 호출은 기존 값을 보존한다.
            return report;
        }

        int summaryBodyStart = summaryStart + SUMMARY_HEADING.length();
        String originalSummary = report.substring(summaryBodyStart, strengthsStart)
                .replaceAll("\\s+", " ")
                .strip();
        String outcomeSummary = passed
                ? "최종 %d점으로 합격 기준 %d점을 충족했습니다.".formatted(totalScore, passingScore)
                : "최종 %d점으로 합격 기준 %d점에는 미치지 못했습니다.".formatted(totalScore, passingScore);

        return report.substring(0, summaryBodyStart)
                + "\n" + outcomeSummary
                + "\n" + originalSummary
                + "\n\n"
                + report.substring(strengthsStart);
    }
}
