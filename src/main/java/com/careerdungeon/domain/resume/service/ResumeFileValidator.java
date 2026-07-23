package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeFileSizeExceededException;
import com.careerdungeon.domain.resume.exception.ResumeFileTypeNotAllowedException;
import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.util.ResumeFileExtension;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class ResumeFileValidator {
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "txt", "md");
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public String validateExtension(String value) {
        String extension = ResumeFileExtension.extract(value);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResumeFileTypeNotAllowedException("upload file");
        }
        return extension;
    }

    public void validateSize(long size) {
        if (size <= 0 || size > MAX_FILE_SIZE_BYTES) {
            throw new ResumeFileSizeExceededException();
        }
    }

    public void validate(String extension, byte[] bytes) {
        validateSize(bytes.length);
        if ("pdf".equals(extension)) validatePdf(bytes);
        else decodeAndValidatePlainText(bytes);
    }

    public void validatePdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) throw invalidPdf();
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) throw invalidPdf();
        }
    }

    public String decodeAndValidatePlainText(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new ResumeParsingFailedException("TXT/MD file is not valid UTF-8 text.", e);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        if (text.isBlank()) throw new ResumeParsingFailedException("TXT/MD file has no extractable text.");
        if (text.indexOf('\u0000') >= 0) throw new ResumeParsingFailedException("TXT/MD file contains a NUL character.");
        int count = text.codePointCount(0, text.length());
        long unsafe = text.codePoints().filter(this::isUnsafeControlCharacter).count();
        if (unsafe * 100 > (long) count) {
            throw new ResumeParsingFailedException("TXT/MD file contains too many invalid control characters.");
        }
        return text;
    }

    private ResumeParsingFailedException invalidPdf() {
        return new ResumeParsingFailedException("PDF magic number(%PDF-) is invalid.");
    }

    private boolean isUnsafeControlCharacter(int c) {
        return Character.isISOControl(c) && c != '\t' && c != '\r' && c != '\n';
    }
}
