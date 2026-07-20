package com.careerdungeon.domain.resume.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class ResumeFileExtension {

    private ResumeFileExtension() {
    }

    public static String extract(String filenameOrKey) {
        String extension = StringUtils.getFilenameExtension(filenameOrKey);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }
}
