package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.service.ResumeFileValidator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfBoxResumeTextExtractorTest {
    private final PdfBoxResumeTextExtractor sut = new PdfBoxResumeTextExtractor(new ResumeFileValidator());

    @Test
    void extractsTextFromBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.showText("Backend Resume");
                content.endText();
            }
            doc.save(out);
        }
        assertThat(sut.extract(out.toByteArray())).contains("Backend Resume");
    }

    @Test
    void rejectsFakePdf() {
        assertThatThrownBy(() -> sut.extract("not-pdf".getBytes()))
                .isInstanceOf(ResumeParsingFailedException.class);
    }

    @Test
    void rejectsPdfWithoutTextLayer() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(out);
        }
        assertThatThrownBy(() -> sut.extract(out.toByteArray()))
                .isInstanceOf(ResumeParsingFailedException.class);
    }
}
