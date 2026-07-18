package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.persona.PersonaPromptProvider;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionGenerationPromptProviderTest {

    private final QuestionGenerationPromptProvider sut =
            new QuestionGenerationPromptProvider(new PersonaPromptProvider());

    @Test
    @DisplayName("페르소나 System Prompt를 재사용하고 이력서, 키워드, JSON 스키마 지시를 조립한다")
    void promptInjectsPersonaResumeKeywordAndSchema() {
        QuestionGenerationPrompt prompt = sut.prompt(new QuestionGenerationRequest(
                "Spring Boot API 성능 개선 경험. DB 실행 계획을 확인하고 인덱스를 조정함.",
                "DB",
                "STRICT",
                "김철수"));

        assertThat(prompt.systemPrompt()).contains("깐깐한 과장");
        assertThat(prompt.systemPrompt()).contains("김철수님");
        assertThat(prompt.userPrompt()).contains("DB");
        assertThat(prompt.userPrompt()).contains("Spring Boot API 성능 개선 경험");
        assertThat(prompt.userPrompt()).contains("\"questions\"");
        assertThat(prompt.userPrompt()).contains("\"turn\": 1");
        assertThat(prompt.userPrompt()).contains("\"questionText\"");
        assertThat(prompt.userPrompt()).contains("\"expectedAnswer\"");
        assertThat(prompt.userPrompt()).contains("questions 배열 크기는 정확히 3");
        assertThat(prompt.userPrompt()).contains("이력서에 선택 키워드와 직접 관련된 경험이 없으면");
        assertThat(prompt.userPrompt()).contains("일반적인 CS 지식 관점에서 질문");
        assertThat(prompt.userPrompt()).contains("매번 새로 생성");
        assertThat(prompt.userPrompt()).contains("미리 정해진 질문 목록에서 고르거나 이전에 생성한 질문을 재사용");
        assertThat(prompt.userPrompt()).contains("few-shot 예시의 문장을 그대로 베끼지 말고");
        assertThat(prompt.userPrompt()).doesNotContain("{{resumeText}}");
        assertThat(prompt.userPrompt()).doesNotContain("{{keyword}}");
    }

    @Test
    @DisplayName("few-shot 예시는 자체 작성된 질문/모범답안 형태로 포함한다")
    void promptContainsFewShotExamples() {
        QuestionGenerationPrompt prompt = sut.prompt(new QuestionGenerationRequest(
                "Spring Security와 OAuth2로 로그인 흐름 구현",
                "보안",
                "LENIENT",
                "홍길동"));

        assertThat(prompt.userPrompt()).contains("Few-shot 예시");
        assertThat(prompt.userPrompt()).contains("실행 계획");
        assertThat(prompt.userPrompt()).contains("refresh token");
        assertThat(prompt.userPrompt()).contains("보안");
        assertThat(prompt.userPrompt()).contains("출력 모범답안");
    }

    @Test
    @DisplayName("꼬리질문 프롬프트는 기존 페르소나 톤과 최저점 문항 맥락, JSON 스키마를 조립한다")
    void followUpPromptInjectsPersonaWeakestQuestionAndSchema() {
        QuestionGenerationPrompt prompt = sut.followUpPrompt(
                "STRICT",
                "김철수",
                2,
                "캐시 정합성 문제를 어떻게 처리했나요?",
                "캐시는 DB 부하를 줄입니다.",
                "정합성 처리 전략이 빠져 있습니다.");

        assertThat(prompt.systemPrompt()).contains("깐깐한 과장");
        assertThat(prompt.systemPrompt()).contains("김철수님");
        assertThat(prompt.userPrompt()).contains("2");
        assertThat(prompt.userPrompt()).contains("캐시 정합성 문제");
        assertThat(prompt.userPrompt()).contains("캐시는 DB 부하를 줄입니다.");
        assertThat(prompt.userPrompt()).contains("정합성 처리 전략");
        assertThat(prompt.userPrompt()).contains("\"followUpQuestion\"");
        assertThat(prompt.userPrompt()).contains("\"expectedAnswer\"");
        assertThat(prompt.userPrompt()).contains("Few-shot 예시");
        assertThat(prompt.userPrompt()).contains("few-shot 예시의 문장을 그대로 베끼지 말고");
        assertThat(prompt.userPrompt()).doesNotContain("{{weakestQuestionId}}");
        assertThat(prompt.userPrompt()).doesNotContain("{{questionText}}");
        assertThat(prompt.userPrompt()).doesNotContain("{{userAnswer}}");
        assertThat(prompt.userPrompt()).doesNotContain("{{feedback}}");
    }

    @Test
    @DisplayName("꼬리질문 프롬프트의 빈 입력은 거부한다")
    void followUpPromptBlankInputThrows() {
        assertThatThrownBy(() -> sut.followUpPrompt(
                        "STRICT",
                        "홍길동",
                        1,
                        " ",
                        "답변",
                        "피드백"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questionText");
    }

    @Test
    @DisplayName("지원하지 않는 personaTone은 거부한다")
    void unsupportedPersonaToneThrows() {
        QuestionGenerationRequest request = new QuestionGenerationRequest(
                "이력서",
                "DB",
                "CASUAL",
                "홍길동");

        assertThatThrownBy(() -> sut.prompt(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("personaTone");
    }

    @Test
    @DisplayName("빈 이력서와 키워드는 프롬프트 조립 전에 거부한다")
    void blankInputThrows() {
        QuestionGenerationRequest blankResume = new QuestionGenerationRequest(" ", "DB", "STRICT", "홍길동");
        QuestionGenerationRequest blankKeyword = new QuestionGenerationRequest("이력서", " ", "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.prompt(blankResume))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resumeText");
        assertThatThrownBy(() -> sut.prompt(blankKeyword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");
    }
}
