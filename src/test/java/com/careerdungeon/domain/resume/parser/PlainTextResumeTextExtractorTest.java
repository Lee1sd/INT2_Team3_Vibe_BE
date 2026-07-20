package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainTextResumeTextExtractorTest {

    private final PlainTextResumeTextExtractor sut = new PlainTextResumeTextExtractor();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("UTF-8 TXT 텍스트를 그대로 추출한다")
    void extract_txt_returnsOriginalText() throws Exception {
        Path file = writeString("resume.txt", "경력\n- Spring Boot\t3년");

        assertThat(sut.extract(file.toString())).isEqualTo("경력\n- Spring Boot\t3년");
    }

    @Test
    @DisplayName("MD 문법을 제거하거나 HTML로 변환하지 않고 그대로 보존한다")
    void extract_markdown_preservesMarkdownSyntax() throws Exception {
        String markdown = "# 경력\n\n* Java\n* **Spring Boot**\n`code`";
        Path file = writeString("resume.md", markdown);

        assertThat(sut.extract(file.toString())).isEqualTo(markdown);
    }

    @Test
    @DisplayName("UTF-8 BOM은 추출 결과에서 제거한다")
    void extract_utf8Bom_removesBom() throws Exception {
        Path file = writeString("resume.txt", "\uFEFF이력서 본문");

        assertThat(sut.extract(file.toString())).isEqualTo("이력서 본문");
    }

    @Test
    @DisplayName("잘못된 UTF-8 바이트열은 파싱 실패로 처리한다")
    void extract_malformedUtf8_throwsException() throws Exception {
        Path file = tempDir.resolve("resume.txt");
        Files.write(file, new byte[]{(byte) 0xC3, 0x28});

        assertThatThrownBy(() -> sut.extract(file.toString()))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    @DisplayName("빈 파일과 공백뿐인 파일은 파싱 실패로 처리한다")
    void extract_blankText_throwsException() throws Exception {
        Path file = writeString("resume.txt", " \t\r\n");

        assertThatThrownBy(() -> sut.extract(file.toString()))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("추출할 텍스트가 없습니다");
    }

    @Test
    @DisplayName("완전히 빈 파일(0바이트)은 파싱 실패로 처리한다")
    void extract_emptyFile_throwsException() throws Exception {
        Path file = writeString("resume.txt", "");

        assertThatThrownBy(() -> sut.extract(file.toString()))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("추출할 텍스트가 없습니다");
    }

    @Test
    @DisplayName("NUL 문자가 포함된 텍스트는 파싱 실패로 처리한다")
    void extract_nulCharacter_throwsException() throws Exception {
        Path file = writeString("resume.txt", "정상 본문\u0000숨은 데이터");

        assertThatThrownBy(() -> sut.extract(file.toString()))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("NUL");
    }

    @Test
    @DisplayName("탭/CR/LF 외 제어문자가 1%를 초과하면 파싱 실패로 처리한다")
    void extract_excessiveControlCharacters_throwsException() throws Exception {
        Path file = writeString("resume.txt", "정상 텍스트" + "\u0001".repeat(2));

        assertThatThrownBy(() -> sut.extract(file.toString()))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("제어문자");
    }

    @Test
    @DisplayName("탭/CR/LF는 정상 텍스트 제어문자로 허용한다")
    void extract_allowedWhitespaceControls_returnsText() throws Exception {
        String text = "첫 줄\r\n둘째 줄\t경력";
        Path file = writeString("resume.txt", text);

        assertThat(sut.extract(file.toString())).isEqualTo(text);
    }

    private Path writeString(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
