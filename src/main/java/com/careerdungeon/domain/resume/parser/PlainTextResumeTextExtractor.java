package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.service.ResumeFileValidator;
import org.springframework.stereotype.Component;

@Component
public class PlainTextResumeTextExtractor {
    private final ResumeFileValidator validator;

    public PlainTextResumeTextExtractor(ResumeFileValidator validator) {
        this.validator = validator;
    }

    public String extract(byte[] fileBytes) {
        return validator.decodeAndValidatePlainText(fileBytes);
    }
}
