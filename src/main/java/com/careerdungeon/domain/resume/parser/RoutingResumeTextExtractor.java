package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

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
    public String extract(String s3Key) throws ResumeParsingFailedException {
        return switch (extractExtension(s3Key)) {
            case "pdf" -> pdfExtractor.extract(s3Key);
            case "txt", "md" -> plainTextExtractor.extract(s3Key);
            default -> throw new ResumeParsingFailedException("지원하지 않는 이력서 파일 확장자입니다.");
        };
    }

    private String extractExtension(String s3Key) {
        String filename = Path.of(s3Key).getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
