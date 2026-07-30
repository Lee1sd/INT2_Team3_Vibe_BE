package com.careerdungeon.global.llm.validation;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * LLM 리포트 형식이 반복해서 이탈해도 실제 면접 컨텍스트를 사용한 4섹션 리포트를 만든다.
 *
 * <p>고정 사과문 대신 질문·답변·서버 확정 피드백을 사용하되, 동적 텍스트가 Markdown
 * 구조나 내부 용어 금지 계약을 깨지 못하도록 한 줄 사용자 텍스트로 정규화한다.
 */
public final class ContextualCareerReportFactory {

    private static final int MAX_CONTEXT_LENGTH = 240;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern MARKDOWN_CONTROL = Pattern.compile("[#*_`>|\\[\\]<>]");
    private static final Pattern TURN_TERM =
            Pattern.compile(
                    "(?i)(?<![a-z])turn(?![a-z])"
                            + "|(?<![가-힣])턴(?=(?:의|은|는|이|가|을|를|과|와|에서|으로|로|마다|별)?"
                            + "(?:$|[^가-힣]))");
    private static final List<TermReplacement> INTERNAL_TERM_REPLACEMENTS = List.of(
            new TermReplacement("transcript", "면접 내용"),
            new TermReplacement("expectedanswer", "평가 참고 내용"),
            new TermReplacement("confirmedscore", "평가 결과"),
            new TermReplacement("모범답안", "평가 참고 내용"),
            new TermReplacement("루브릭", "평가 기준"),
            new TermReplacement("🎯 총평", "전체 평가"),
            new TermReplacement("✨ 이런 점이 매우 훌륭했어요", "강점 내용"),
            new TermReplacement("🚀 합격을 확정 짓는 2%", "보완할 핵심"),
            new TermReplacement("💡 next step", "다음 학습"),
            new TermReplacement("❌ as-is", "기존 답변"),
            new TermReplacement("⭕ to-be", "개선 답변"),
            new TermReplacement("추가 검토 지점", "추가로 검토할 내용"),
            new TermReplacement("결론", "답변의 결론"));

    private ContextualCareerReportFactory() {
    }

    /**
     * 최초 응답의 점수는 건드리지 않고, 실제 면접 입력과 확정 피드백으로 사용자 리포트를 만든다.
     */
    public static String create(EvaluationRequest request, FinalEvaluationResponse response) {
        QuestionAnswerPair followUp = request.questionAnswerPairs().get(0);
        QuestionEvaluation followUpEvaluation = response.evaluations().get(0);
        List<PreviousEvaluationContext> contexts = request.previousEvaluations();

        PreviousEvaluationContext strongest = contexts.stream()
                .max(Comparator.comparingInt(PreviousEvaluationContext::score))
                .orElse(contexts.get(0));
        PreviousEvaluationContext weakest = contexts.stream()
                .min(Comparator.comparingInt(PreviousEvaluationContext::score))
                .orElse(contexts.get(0));

        String userName = inline(request.userName(), "지원자");
        String followUpFeedback = inline(
                followUpEvaluation.feedback(),
                "꼬리질문에서 선택 근거와 검증 방법을 더 구체적으로 설명하면 좋겠습니다.");

        return """
                🎯 총평
                %s님의 답변은 실제 면접 질문의 흐름을 끝까지 이어 갔습니다. %s

                ✨ 이런 점이 매우 훌륭했어요
                - `%s` 질문에 본인의 접근과 판단을 직접 설명했습니다.
                - `%s` 꼬리질문에도 직접 답하며 면접 전반의 문제 해결 흐름을 이어 갔습니다.

                🚀 합격을 확정 짓는 2%%
                `%s` 답변에서는 `%s`라는 보완점이 확인됐고, 꼬리질문에서도 `%s`라는 점을 더 구체화할 필요가 있습니다.

                💡 Next Step
                ❌ AS-IS (지원자의 기존 답변 방식)
                %s

                ⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)
                선택한 해결 방법의 근거, 실패 대응, 검증 계획을 한 흐름으로 설명하고, 적용 전후를 [예: p95 응답 시간 320ms → 140ms]와 [예: 오류율 2%% → 0.5%%]로 비교해 제시해 보세요.
                """.formatted(
                userName,
                followUpFeedback,
                inline(strongest.questionText(), "핵심 기술"),
                inline(followUp.questionText(), "문제 해결"),
                inline(weakest.questionText(), "보완이 필요한 질문"),
                inline(weakest.feedback(), "운영 근거와 예외 상황"),
                followUpFeedback,
                inline(followUp.userAnswer(), "꼬리질문에 대한 기존 답변"));
    }

    /** 동적 텍스트를 Markdown 구조를 만들 수 없는 길이 제한 한 줄로 정규화한다. */
    private static String inline(String value, String fallback) {
        String normalized = value == null ? "" : value;
        normalized = TURN_TERM.matcher(normalized).replaceAll("문항");
        for (TermReplacement replacement : INTERNAL_TERM_REPLACEMENTS) {
            normalized = replaceIgnoreCase(normalized, replacement.target(), replacement.replacement());
        }
        normalized = normalizeKoreanParticles(normalized);
        normalized = MARKDOWN_CONTROL.matcher(normalized).replaceAll(" ");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").strip();
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        return truncate(normalized, MAX_CONTEXT_LENGTH);
    }

    /** 금지 내부 용어는 대소문자와 관계없이 사용자 친화 용어로 바꾼다. */
    private static String replaceIgnoreCase(String value, String target, String replacement) {
        String lowerValue = value.toLowerCase(Locale.ROOT);
        String lowerTarget = target.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        int start = 0;
        int index;
        while ((index = lowerValue.indexOf(lowerTarget, start)) >= 0) {
            result.append(value, start, index).append(replacement);
            start = index + target.length();
        }
        return result.append(value.substring(start)).toString();
    }

    /** 영문 내부 용어를 한글로 치환한 뒤 붙어 있던 조사를 자연스럽게 보정한다. */
    private static String normalizeKoreanParticles(String value) {
        return value
                .replace("평가 참고 내용가", "평가 참고 내용이")
                .replace("평가 참고 내용는", "평가 참고 내용은")
                .replace("평가 참고 내용를", "평가 참고 내용을");
    }

    /** UTF-16 surrogate를 자르지 않으면서 사용자 노출 컨텍스트 길이를 제한한다. */
    private static String truncate(String value, int maxCodePoints) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxCodePoints) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, endIndex).stripTrailing() + "…";
    }

    /** 내부 용어 치환 규칙을 값 객체로 보관한다. */
    private record TermReplacement(String target, String replacement) {
    }
}
