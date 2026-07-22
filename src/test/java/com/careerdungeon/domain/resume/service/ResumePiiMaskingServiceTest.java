package com.careerdungeon.domain.resume.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumePiiMaskingServiceTest {

    private final ResumePiiMaskingService service = new ResumePiiMaskingService();

    @Test
    void masksEmail() {
        String text = "이메일 hong.gildong+career@example.com, 연락처 010-1234-5678";

        String masked = service.mask(text);

        assertThat(masked).isEqualTo("이메일 [EMAIL], 연락처 010-1234-5678");
    }

    @Test
    void masksEmailFollowedBySentencePeriod() {
        assertThat(service.mask("연락처는 user@example.com. 입니다"))
                .isEqualTo("연락처는 [EMAIL]. 입니다");
    }

    @Test
    void preservesTextWithoutPiiAndHandlesNull() {
        assertThat(service.mask("Java와 Spring 경력 3년")).isEqualTo("Java와 Spring 경력 3년");
        assertThat(service.mask(null)).isNull();
    }
}
