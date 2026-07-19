package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfBoxResumeTextExtractorTest {

    private final PdfBoxResumeTextExtractor sut = new PdfBoxResumeTextExtractor();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("%PDF- 매직넘버와 유효한 PDF 구조를 모두 통과하면 텍스트를 추출한다")
    void extract_validPdf_returnsText() throws Exception {
        Path file = createPdf("Backend Resume");

        assertThat(sut.extract(file.toString())).contains("Backend Resume");
    }

    @Test
    @DisplayName("%PDF- 매직넘버가 없으면 PDFBox 파싱 전에 거부한다")
    void extract_missingMagicNumber_throwsException() throws Exception {
        Path file = tempDir.resolve("fake.pdf");
        Files.writeString(file, "not a pdf", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> sut.extract(file.toString()))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("매직넘버");
    }

    @Test
    @DisplayName("매직넘버만 위조하고 PDF 구조가 손상된 파일은 PDFBox가 거부한다")
    void extract_magicOnlyButCorrupt_throwsException() throws Exception {
        Path file = tempDir.resolve("corrupt.pdf");
        Files.writeString(file, "%PDF-not-a-real-document", StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> sut.extract(file.toString()))
                .isInstanceOf(ResumeParsingFailedException.class)
                .hasMessageContaining("PDF 파싱 중 오류");
    }

    private Path createPdf(String content) throws Exception {
        Path file = tempDir.resolve("resume.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(content);
                stream.endText();
            }
            document.save(file.toFile());
        }
        return file;
    }
}
