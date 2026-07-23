package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.service.ResumeFileValidator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfBoxResumeTextExtractor {
    private final ResumeFileValidator validator;

    public PdfBoxResumeTextExtractor(ResumeFileValidator validator) {
        this.validator = validator;
    }

    public String extract(byte[] fileBytes) {
        validator.validatePdf(fileBytes);
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                throw new ResumeParsingFailedException("PDF has no extractable text.");
            }
            return text;
        } catch (InvalidPasswordException e) {
            throw new ResumeParsingFailedException("Password-protected PDF cannot be parsed.", e);
        } catch (IOException e) {
            throw new ResumeParsingFailedException("PDF parsing failed.", e);
        }
    }
}
