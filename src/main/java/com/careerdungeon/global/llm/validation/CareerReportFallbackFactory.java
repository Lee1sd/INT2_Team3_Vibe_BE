package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * LLM 종합 리포트의 형식이 깨졌을 때 실제 문항별 평가를 반영한 결정형 리포트를 만든다.
 *
 * <p>추가 LLM 호출 없이도 모든 사용자에게 같은 문구가 노출되지 않도록, 서버 확정 이전
 * 평가 컨텍스트와 꼬리질문 평가에서 강점과 보완점을 선택한다. 사용자 입력과 LLM 피드백은
 * Markdown 구조를 깨거나 내부 용어를 노출하지 않도록 한 줄 텍스트로 정규화한다.
 */
public final class CareerReportFallbackFactory {

    private static final int MAX_EVIDENCE_LENGTH = 180;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern MARKDOWN_CONTROL = Pattern.compile("[#>`*_~]");
    private static final Pattern TURN_TERM = Pattern.compile("(?i)(?<![a-z])turn(?![a-z])");
    private static final List<String> PROHIBITED_TERMS = List.of(
            "Transcript",
            "expectedAnswer",
            "모범답안",
            "confirmedScore",
            "루브릭");

    private CareerReportFallbackFactory() {
    }

    /**
     * 실제 질문·답변·문항별 평가를 바탕으로 네 섹션 리포트를 조립한다.
     *
     * @param request 최종 채점 요청과 최초 네 문항의 확정 평가 컨텍스트
     * @param response 꼬리질문 평가 응답
     * @return {@link CareerReportValidator} 계약을 충족하는 결정형 리포트
     */
    public static String create(EvaluationRequest request, FinalEvaluationResponse response) {
        List<Evidence> evidence = collectEvidence(request, response);
        Evidence weakest = evidence.stream()
                .min(Comparator.comparingInt(Evidence::score).thenComparingInt(Evidence::turn))
                .orElseThrow();
        List<Evidence> strongest = evidence.stream()
                .sorted(Comparator.comparingInt(Evidence::score).reversed()
                        .thenComparingInt(Evidence::turn))
                .limit(2)
                .toList();

        String userName = safeText(request.userName(), "지원자", 30);
        String firstStrength = strengthSentence(strongest.get(0));
        String secondStrength = strengthSentence(strongest.get(1));
        String weakestFeedback = safeText(
                weakest.feedback(),
                "핵심 결론을 뒷받침할 근거와 예외 상황 설명을 보완할 필요가 있습니다.",
                MAX_EVIDENCE_LENGTH);
        String weakestAnswer = safeText(
                weakest.answer(),
                "핵심 방향은 설명했지만 판단 근거를 충분히 연결하지 못했습니다.",
                MAX_EVIDENCE_LENGTH);

        String report = """
                🎯 총평
                %s님의 실제 문항별 평가를 기준으로 강점과 보완점을 정리했습니다.

                ✨ 이런 점이 매우 훌륭했어요
                - %s
                - %s

                🚀 합격을 확정 짓는 2%%
                가장 보완이 필요한 질문 %d 답변에서는 %s

                💡 Next Step
                ❌ AS-IS (지원자의 기존 답변 방식)
                “%s”라고 답해 핵심 방향은 제시했지만, 선택 근거와 검증 기준을 더 분명히 연결할 필요가 있습니다.

                ⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)
                결론, 선택 근거, 예외 상황, 검증 방법을 순서대로 설명하고 개선 전후를 [예: p95 응답 시간 240ms → 120ms]처럼 구분해 제시해 보세요.
                """.formatted(
                userName,
                firstStrength,
                secondStrength,
                weakest.turn(),
                weakestFeedback,
                weakestAnswer);
        return CareerReportValidator.appendHypotheticalDisclaimer(report);
    }

    /** 최초 네 문항과 꼬리질문 평가를 같은 선택 기준으로 비교할 수 있게 합친다. */
    private static List<Evidence> collectEvidence(
            EvaluationRequest request,
            FinalEvaluationResponse response) {
        List<Evidence> evidence = new ArrayList<>();
        for (PreviousEvaluationContext context : request.previousEvaluations()) {
            evidence.add(new Evidence(
                    context.turn(),
                    clampScore(context.score()),
                    context.userAnswer(),
                    context.feedback()));
        }

        QuestionAnswerPair followUpPair = request.questionAnswerPairs().get(0);
        QuestionEvaluation followUpEvaluation = response.evaluations().stream()
                .filter(evaluation -> evaluation.turn() == followUpPair.turn())
                .findFirst()
                .orElseThrow();
        evidence.add(new Evidence(
                followUpPair.turn(),
                clampScore(followUpEvaluation.score()),
                followUpPair.userAnswer(),
                followUpEvaluation.feedback()));
        return List.copyOf(evidence);
    }

    /** 점수가 높은 답변의 실제 평가 문장을 강점 불릿에 맞게 정리한다. */
    private static String strengthSentence(Evidence evidence) {
        String feedback = safeText(
                evidence.feedback(),
                "질문의 핵심 방향을 설명하려고 한 점이 확인됐습니다.",
                MAX_EVIDENCE_LENGTH);
        return "질문 " + evidence.turn() + " 답변 평가에서 " + feedback;
    }

    /**
     * 외부 생성 텍스트를 한 줄로 만들고 리포트 계약의 내부 용어와 Markdown 제어 문자를 제거한다.
     */
    private static String safeText(String value, String fallback, int maximumLength) {
        String sanitized = value == null ? "" : value;
        sanitized = WHITESPACE.matcher(sanitized).replaceAll(" ").strip();
        sanitized = MARKDOWN_CONTROL.matcher(sanitized).replaceAll("");
        sanitized = TURN_TERM.matcher(sanitized).replaceAll("문항");
        for (String prohibitedTerm : PROHIBITED_TERMS) {
            sanitized = sanitized.replaceAll(
                    "(?i)" + Pattern.quote(prohibitedTerm),
                    prohibitedTerm.toLowerCase(Locale.ROOT).equals("transcript") ? "답변 내용" : "평가 기준");
        }
        sanitized = sanitized
                .replace(CareerReportValidator.HYPOTHETICAL_DISCLAIMER, "")
                .replace(CareerReportValidator.SUMMARY_HEADING, "")
                .replace(CareerReportValidator.STRENGTHS_HEADING, "")
                .replace(CareerReportValidator.GROWTH_HEADING, "")
                .replace(CareerReportValidator.NEXT_STEP_HEADING, "")
                .replace(CareerReportValidator.AS_IS_HEADING, "")
                .replace(CareerReportValidator.TO_BE_HEADING, "")
                .strip();
        if (sanitized.isBlank()) {
            sanitized = fallback;
        }
        return truncateByCodePoint(sanitized, maximumLength);
    }

    /** 이모지의 surrogate pair를 자르지 않으면서 사용자 노출 문장의 최대 길이를 제한한다. */
    private static String truncateByCodePoint(String value, int maximumLength) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maximumLength) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(0, maximumLength);
        return value.substring(0, endIndex).stripTrailing() + "…";
    }

    /** LLM 보고 점수는 강약 선택에만 쓰므로 문항 범위로 제한한다. */
    private static int clampScore(int score) {
        return Math.max(0, Math.min(20, score));
    }

    /** 리포트 조립에 필요한 사용자 답변과 평가 근거를 묶는다. */
    private record Evidence(int turn, int score, String answer, String feedback) {
    }
}
