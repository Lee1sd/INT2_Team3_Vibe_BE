package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.util.ResumeFileExtension;
import org.springframework.stereotype.Component;

@Component
public class RoutingResumeTextExtractor implements ResumeTextExtractor {
    private final PdfBoxResumeTextExtractor pdfExtractor;
    private final PlainTextResumeTextExtractor plainTextExtractor;

    public RoutingResumeTextExtractor(PdfBoxResumeTextExtractor pdfExtractor,
                                      PlainTextResumeTextExtractor plainTextExtractor) {
        this.pdfExtractor = pdfExtractor;
        this.plainTextExtractor = plainTextExtractor;
    }

    @Override
    public String extract(String s3Key, byte[] fileBytes) {
        return switch (ResumeFileExtension.extract(s3Key)) {
            case "pdf" -> pdfExtractor.extract(fileBytes);
            case "txt", "md" -> plainTextExtractor.extract(fileBytes);
            default -> throw new ResumeParsingFailedException("Unsupported resume file extension.");
        };
    }
}
