package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.service.ResumeFileValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainTextResumeTextExtractorTest {
    private final PlainTextResumeTextExtractor sut =
            new PlainTextResumeTextExtractor(new ResumeFileValidator());

    @Test
    void extractsUtf8Bytes() {
        assertThat(sut.extract("경력\nSpring".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("경력\nSpring");
    }

    @Test
    void rejectsMalformedUtf8() {
        assertThatThrownBy(() -> sut.extract(new byte[]{(byte) 0xC3, (byte) 0x28}))
                .isInstanceOf(ResumeParsingFailedException.class);
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> sut.extract(" \n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResumeParsingFailedException.class);
    }
}
