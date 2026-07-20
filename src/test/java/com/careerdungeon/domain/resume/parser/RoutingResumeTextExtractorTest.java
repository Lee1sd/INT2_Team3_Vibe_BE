package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoutingResumeTextExtractorTest {

    @Mock
    private PdfBoxResumeTextExtractor pdfExtractor;

    @Mock
    private PlainTextResumeTextExtractor plainTextExtractor;

    private RoutingResumeTextExtractor sut;

    @BeforeEach
    void setUp() {
        sut = new RoutingResumeTextExtractor(pdfExtractor, plainTextExtractor);
    }

    @Test
    @DisplayName("pdf 확장자는 PDF 추출기로 위임한다")
    void extract_pdf_delegatesToPdfExtractor() {
        given(pdfExtractor.extract("resume.pdf")).willReturn("pdf text");

        assertThat(sut.extract("resume.pdf")).isEqualTo("pdf text");
        verify(pdfExtractor).extract("resume.pdf");
        verifyNoInteractions(plainTextExtractor);
    }

    @Test
    @DisplayName("txt와 md 확장자는 평문 추출기로 위임하고 대소문자를 구분하지 않는다")
    void extract_textExtensions_delegateToPlainTextExtractor() {
        given(plainTextExtractor.extract("resume.TXT")).willReturn("txt text");
        given(plainTextExtractor.extract("career.resume.Md")).willReturn("md text");

        assertThat(sut.extract("resume.TXT")).isEqualTo("txt text");
        assertThat(sut.extract("career.resume.Md")).isEqualTo("md text");
        verify(plainTextExtractor).extract("resume.TXT");
        verify(plainTextExtractor).extract("career.resume.Md");
        verifyNoInteractions(pdfExtractor);
    }

    @Test
    @DisplayName("OS 경로에서 허용되지 않는 문자가 포함된 S3 키도 문자열로 확장자를 추출한다")
    void extract_s3KeyWithOsInvalidCharacters_delegatesWithoutPathConversion() {
        String s3Key = "resumes/user:1?/career.MD";
        given(plainTextExtractor.extract(s3Key)).willReturn("md text");

        assertThat(sut.extract(s3Key)).isEqualTo("md text");
        verify(plainTextExtractor).extract(s3Key);
        verifyNoInteractions(pdfExtractor);
    }

    @Test
    @DisplayName("지원하지 않는 확장자는 어떤 추출기도 호출하지 않고 거부한다")
    void extract_unsupportedExtension_throwsException() {
        assertThatThrownBy(() -> sut.extract("resume.exe"))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("지원하지 않는");
        verifyNoInteractions(pdfExtractor, plainTextExtractor);
    }
}
