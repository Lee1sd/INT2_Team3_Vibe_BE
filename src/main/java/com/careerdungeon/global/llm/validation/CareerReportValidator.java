package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 사용자에게 노출되는 최종 커리어 리포트의 Markdown 계약을 검증한다.
 *
 * <p>프롬프트 지시만으로는 생성형 응답의 제목 순서나 가상 수치 표기를 보장할 수 없으므로,
 * 저장 전에 필수 섹션과 금지 표현을 다시 확인한다.
 */
public final class CareerReportValidator {

    static final String SUMMARY_HEADING = "🎯 총평";
    static final String STRENGTHS_HEADING = "✨ 이런 점이 매우 훌륭했어요";
    static final String GROWTH_HEADING = "🚀 합격을 확정 짓는 2%";
    static final String NEXT_STEP_HEADING = "💡 Next Step";
    static final String AS_IS_HEADING = "❌ AS-IS (지원자의 기존 답변 방식)";
    static final String TO_BE_HEADING = "⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)";
    static final String HYPOTHETICAL_DISCLAIMER =
            "※ 아래 수치는 답변 구조를 보여주기 위한 가상 예시이며, 실제 측정 결과가 아닙니다.";
    public static final String FALLBACK_REPORT =
            "죄송합니다, 이번 세션의 종합 리포트를 생성하는 중 문제가 발생해 상세 리포트를 "
                    + "표시할 수 없습니다. 문항별 점수와 합격 여부는 정상적으로 반영되었습니다.";

    private static final List<String> SECTION_HEADINGS = List.of(
            SUMMARY_HEADING,
            STRENGTHS_HEADING,
            GROWTH_HEADING,
            NEXT_STEP_HEADING);
    private static final List<String> PROHIBITED_TERMS = List.of(
            "Transcript",
            "expectedAnswer",
            "모범답안",
            "confirmedScore",
            "루브릭");
    private static final Pattern TURN_TERM =
            Pattern.compile("(?i)(?<![a-z])turn(?![a-z])");
    private static final Pattern MARKDOWN_HEADING =
            Pattern.compile("(?m)^#{1,6}\\s+");
    private static final Pattern EXAMPLE_VALUE =
            Pattern.compile("\\[예:[^\\]]+]");
    private static final Pattern NUMBER =
            Pattern.compile("\\d");

    /**
     * {@link #validate(String)}와 동일한 계약을 검사하되 예외를 던지지 않는다. 통과하면
     * 원본 리포트를, 실패하면 안전한 대체 문구({@link #FALLBACK_REPORT})를 반환한다.
     * 이미 확정된 점수(evaluations/totalScore/passed)는 리포트 형식 문제와 무관하게
     * 보존해야 하므로, 리포트만 대체하고 호출자에게 예외를 전파하지 않는다(#167).
     */
    public String validateOrFallback(String report) {
        try {
            validate(report);
            return report;
        } catch (LlmSchemaValidationException e) {
            return FALLBACK_REPORT;
        }
    }

    /** 네 개 섹션과 정량 답변 예시가 사용자 노출 계약을 모두 지키는지 검증한다. */
    public void validate(String report) {
        if (report == null || report.isBlank()) {
            throw new LlmSchemaValidationException("overallFeedback이 비어 있습니다.");
        }

        String normalized = report.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = normalized.lines().toList();
        validateSectionOrder(lines);
        validateSectionBody(lines, SUMMARY_HEADING, STRENGTHS_HEADING);
        validateSummaryLength(lines);
        validateStrengthBullets(lines);
        validateSectionBody(lines, GROWTH_HEADING, NEXT_STEP_HEADING);
        validateNextStep(lines);
        validateProhibitedTerms(normalized);
        validateAdditionalHeadings(lines, normalized);
    }

    /** 총평이 프롬프트 계약인 비어 있지 않은 1~2줄인지 확인한다. */
    private void validateSummaryLength(List<String> lines) {
        int start = lines.indexOf(SUMMARY_HEADING) + 1;
        int end = lines.indexOf(STRENGTHS_HEADING);
        long contentLineCount = lines.subList(start, end).stream()
                .filter(line -> !line.isBlank())
                .count();
        if (contentLineCount < 1 || contentLineCount > 2) {
            throw new LlmSchemaValidationException(
                    "overallFeedback 총평은 1~2줄이어야 합니다.");
        }
    }

    /** 두 제목 사이에 사용자에게 보여줄 본문이 한 줄 이상 존재하는지 확인한다. */
    private void validateSectionBody(List<String> lines, String startHeading, String endHeading) {
        int start = lines.indexOf(startHeading) + 1;
        int end = lines.indexOf(endHeading);
        if (lines.subList(start, end).stream().allMatch(String::isBlank)) {
            throw new LlmSchemaValidationException(
                    "overallFeedback 섹션 본문이 비어 있습니다: " + startHeading);
        }
    }

    /** 필수 섹션 제목이 각각 한 번씩 정확한 순서로 존재하는지 검증한다. */
    private void validateSectionOrder(List<String> lines) {
        int previousIndex = -1;
        for (String heading : SECTION_HEADINGS) {
            long count = lines.stream().filter(heading::equals).count();
            int currentIndex = lines.indexOf(heading);
            if (count != 1 || currentIndex <= previousIndex) {
                throw new LlmSchemaValidationException(
                        "overallFeedback 섹션 제목과 순서가 올바르지 않습니다: " + heading);
            }
            previousIndex = currentIndex;
        }
    }

    /** Strengths 섹션이 내용이 있는 불릿 두 개만 포함하는지 검증한다. */
    private void validateStrengthBullets(List<String> lines) {
        int start = lines.indexOf(STRENGTHS_HEADING) + 1;
        int end = lines.indexOf(GROWTH_HEADING);
        List<String> sectionLines = lines.subList(start, end);
        List<String> bullets = sectionLines.stream()
                .filter(line -> line.startsWith("- "))
                .toList();
        if (bullets.size() != 2 || bullets.stream().anyMatch(line -> line.substring(2).isBlank())) {
            throw new LlmSchemaValidationException(
                    "overallFeedback Strengths는 내용이 있는 불릿 두 개여야 합니다.");
        }
        if (sectionLines.stream()
                .filter(line -> !line.isBlank())
                .anyMatch(line -> !line.startsWith("- "))) {
            throw new LlmSchemaValidationException(
                    "overallFeedback Strengths에는 불릿 외 문장을 추가할 수 없습니다.");
        }
    }

    /** Next Step의 AS-IS/TO-BE 순서와 가상 수치 표기 규칙을 검증한다. */
    private void validateNextStep(List<String> lines) {
        int nextStepIndex = lines.indexOf(NEXT_STEP_HEADING);
        int asIsIndex = uniqueLineIndex(lines, AS_IS_HEADING);
        int toBeIndex = uniqueLineIndex(lines, TO_BE_HEADING);
        if (asIsIndex <= nextStepIndex || toBeIndex <= asIsIndex) {
            throw new LlmSchemaValidationException(
                    "overallFeedback Next Step의 AS-IS/TO-BE 순서가 올바르지 않습니다.");
        }
        if (lines.subList(asIsIndex + 1, toBeIndex).stream().allMatch(String::isBlank)) {
            throw new LlmSchemaValidationException(
                    "overallFeedback AS-IS 답변 예시가 비어 있습니다.");
        }

        // 가상 수치 고지는 모델 응답을 신뢰하지 않고 서버가 TO-BE 섹션 끝에 항상 덧붙인다
        // (appendHypotheticalDisclaimer). 여기서는 모델이 만든 TO-BE 본문 자체만 검증한다.
        String toBeBody = String.join("\n", lines.subList(toBeIndex + 1, lines.size()));
        if (toBeBody.isBlank() || !EXAMPLE_VALUE.matcher(toBeBody).find()) {
            throw new LlmSchemaValidationException(
                    "overallFeedback TO-BE에는 [예: ...] 정량 지표가 필요합니다.");
        }
        String withoutMarkedExamples = EXAMPLE_VALUE.matcher(toBeBody).replaceAll("");
        if (NUMBER.matcher(withoutMarkedExamples).find()) {
            throw new LlmSchemaValidationException(
                    "overallFeedback TO-BE의 모든 가상 수치는 [예: ...]로 표시해야 합니다.");
        }
    }

    /**
     * 가상 수치 고지를 모델 응답 여부와 무관하게 TO-BE 섹션(리포트) 끝에 항상 덧붙인다.
     * {@link #validate(String)}를 통과한 리포트에만 호출해야 한다.
     */
    public static String appendHypotheticalDisclaimer(String report) {
        return report.stripTrailing() + "\n\n" + HYPOTHETICAL_DISCLAIMER;
    }

    /** 한 줄짜리 필수 표지가 중복되거나 누락되지 않았는지 확인한다. */
    private int uniqueLineIndex(List<String> lines, String expectedLine) {
        long count = lines.stream().filter(expectedLine::equals).count();
        if (count != 1) {
            throw new LlmSchemaValidationException(
                    "overallFeedback에 필수 표지가 정확히 한 번 필요합니다: " + expectedLine);
        }
        return lines.indexOf(expectedLine);
    }

    /** 사용자 리포트에 내부 처리 용어가 노출되지 않는지 검증한다. */
    private void validateProhibitedTerms(String report) {
        for (String prohibitedTerm : PROHIBITED_TERMS) {
            if (report.toLowerCase(Locale.ROOT)
                    .contains(prohibitedTerm.toLowerCase(Locale.ROOT))) {
                throw new LlmSchemaValidationException(
                        "overallFeedback에 내부 처리 용어를 사용할 수 없습니다: " + prohibitedTerm);
            }
        }
        if (TURN_TERM.matcher(report).find()) {
            throw new LlmSchemaValidationException(
                    "overallFeedback에 내부 처리 용어를 사용할 수 없습니다: turn");
        }
    }

    /** 약속된 네 섹션 외 Markdown 제목이나 임의 결론 섹션을 차단한다. */
    private void validateAdditionalHeadings(List<String> lines, String report) {
        if (MARKDOWN_HEADING.matcher(report).find()
                || lines.stream().anyMatch(line -> line.equals("추가 검토 지점") || line.equals("결론"))) {
            throw new LlmSchemaValidationException(
                    "overallFeedback에 지정되지 않은 추가 섹션을 사용할 수 없습니다.");
        }
    }
}
