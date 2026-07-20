package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PlainTextResumeTextExtractor {

    private static final char UTF_8_BOM = '\uFEFF';
    private static final int MAX_UNSAFE_CONTROL_PERCENT = 1;

    public String extract(String s3Key) throws ResumeParsingFailedException {
        String text = readUtf8(s3Key);
        if (!text.isEmpty() && text.charAt(0) == UTF_8_BOM) {
            text = text.substring(1);
        }
        validateText(text);
        return text;
    }

    private String readUtf8(String s3Key) {
        try {
            return Files.readString(Path.of(s3Key), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResumeParsingFailedException("TXT/MD 파일을 올바른 UTF-8 텍스트로 읽을 수 없습니다.", e);
        }
    }

    private void validateText(String text) {
        if (text.isBlank()) {
            throw new ResumeParsingFailedException("TXT/MD 파일에서 추출할 텍스트가 없습니다.");
        }
        if (text.indexOf('\u0000') >= 0) {
            throw new ResumeParsingFailedException("TXT/MD 파일에 NUL 문자가 포함되어 있습니다.");
        }

        int codePointCount = text.codePointCount(0, text.length());
        long unsafeControlCount = text.codePoints()
                .filter(this::isUnsafeControlCharacter)
                .count();
        if (unsafeControlCount * 100 > (long) codePointCount * MAX_UNSAFE_CONTROL_PERCENT) {
            throw new ResumeParsingFailedException("TXT/MD 파일에 비정상 제어문자가 과도하게 포함되어 있습니다.");
        }
    }

    private boolean isUnsafeControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\t'
                && codePoint != '\r'
                && codePoint != '\n';
    }
}
