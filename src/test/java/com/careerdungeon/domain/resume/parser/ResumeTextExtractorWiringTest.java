package com.careerdungeon.domain.resume.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.careerdungeon.domain.resume.service.ResumeFileValidator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {
        RoutingResumeTextExtractor.class,
        PdfBoxResumeTextExtractor.class,
        PlainTextResumeTextExtractor.class,
        ResumeFileValidator.class
})
class ResumeTextExtractorWiringTest {

    @Autowired
    private ResumeTextExtractor resumeTextExtractor;

    @Test
    @DisplayName("ResumeTextExtractor 인터페이스는 항상 형식별 라우팅 구현으로 주입된다")
    void resumeTextExtractor_isAlwaysRoutingImplementation() {
        assertThat(resumeTextExtractor).isInstanceOf(RoutingResumeTextExtractor.class);
    }
}
