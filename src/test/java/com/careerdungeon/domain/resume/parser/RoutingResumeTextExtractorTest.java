package com.careerdungeon.domain.resume.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RoutingResumeTextExtractorTest {
    private final PdfBoxResumeTextExtractor pdf = mock(PdfBoxResumeTextExtractor.class);
    private final PlainTextResumeTextExtractor plain = mock(PlainTextResumeTextExtractor.class);
    private final RoutingResumeTextExtractor sut = new RoutingResumeTextExtractor(pdf, plain);

    @Test
    void routesByS3KeyExtensionAndPassesBytes() {
        byte[] bytes = "text".getBytes();
        given(plain.extract(bytes)).willReturn("text");
        assertThat(sut.extract("resumes/1/pending/id.MD", bytes)).isEqualTo("text");
    }
}
