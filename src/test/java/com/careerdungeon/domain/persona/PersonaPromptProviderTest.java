package com.careerdungeon.domain.persona;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaPromptProviderTest {

    private final PersonaPromptProvider sut = new PersonaPromptProvider();

    @Test
    @DisplayName("LENIENT 톤 → 널널한 대리 System Prompt 로드, 이름 주입")
    void lenient_loadsTemplateAndInjectsUserName() {
        String prompt = sut.systemPrompt(PersonaTone.LENIENT, "홍길동");

        assertThat(prompt).contains("널널한 대리");
        assertThat(prompt).contains("홍길동님");
        assertThat(prompt).doesNotContain("{{userName}}");
    }

    @Test
    @DisplayName("STRICT 톤 → 깐깐한 과장 System Prompt 로드, 이름 주입")
    void strict_loadsTemplateAndInjectsUserName() {
        String prompt = sut.systemPrompt(PersonaTone.STRICT, "김철수");

        assertThat(prompt).contains("깐깐한 과장");
        assertThat(prompt).contains("김철수님");
        assertThat(prompt).doesNotContain("{{userName}}");
    }

    @Test
    @DisplayName("서로 다른 톤은 서로 다른 System Prompt 내용을 반환한다")
    void differentTones_produceDifferentPrompts() {
        String lenientPrompt = sut.systemPrompt(PersonaTone.LENIENT, "홍길동");
        String strictPrompt = sut.systemPrompt(PersonaTone.STRICT, "홍길동");

        assertThat(lenientPrompt).isNotEqualTo(strictPrompt);
    }

    @Test
    @DisplayName("같은 톤을 반복 호출해도 매번 정상적인 결과를 반환한다 (템플릿 캐시)")
    void sameTone_repeatedCalls_returnConsistentResult() {
        String first = sut.systemPrompt(PersonaTone.LENIENT, "홍길동");
        String second = sut.systemPrompt(PersonaTone.LENIENT, "홍길동");

        assertThat(first).isEqualTo(second);
    }
}
