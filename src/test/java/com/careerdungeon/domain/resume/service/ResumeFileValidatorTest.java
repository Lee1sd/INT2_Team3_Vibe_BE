package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeFileSizeExceededException;
import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeFileValidatorTest {
    private final ResumeFileValidator sut = new ResumeFileValidator();

    @Test
    void validatesPdfMagicNumberFromBytes() {
        byte[] pdf = "%PDF-1.7 content".getBytes(StandardCharsets.US_ASCII);
        sut.validate("pdf", pdf);
    }

    @Test
    void rejectsExtensionSpoofedPdf() {
        assertThatThrownBy(() -> sut.validate("pdf", "plain text".getBytes()))
                .isInstanceOf(ResumeParsingFailedException.class);
    }

    @Test
    void strictlyDecodesUtf8AndRemovesBom() {
        byte[] bytes = "\uFEFF한글 resume".getBytes(StandardCharsets.UTF_8);
        assertThat(sut.decodeAndValidatePlainText(bytes)).isEqualTo("한글 resume");
    }

    @Test
    void rejectsActualBytesOverTenMegabytes() {
        assertThatThrownBy(() -> sut.validate("txt",
                new byte[(int) ResumeFileValidator.MAX_FILE_SIZE_BYTES + 1]))
                .isInstanceOf(ResumeFileSizeExceededException.class);
    }

    @Test
    void rejectsNulCharacterInPlainText() {
        assertThatThrownBy(() -> sut.decodeAndValidatePlainText(
                "resume\u0000text".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResumeParsingFailedException.class);
    }

    @Test
    void rejectsTooManyUnsafeControlCharacters() {
        assertThatThrownBy(() -> sut.decodeAndValidatePlainText(
                "a\u0001b".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResumeParsingFailedException.class);
    }
}
