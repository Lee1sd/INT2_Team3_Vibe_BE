package com.careerdungeon.domain.resume.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ResumePiiMaskingService {

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])");
    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return EMAIL.matcher(text).replaceAll("[EMAIL]");
    }
}
