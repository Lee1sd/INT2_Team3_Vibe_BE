package com.careerdungeon.domain.resume.parser;

import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PdfBoxResumeTextExtractor {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public String extract(String s3Key) throws ResumeParsingFailedException {
        byte[] fileBytes = fetchOriginalBytes(s3Key);
        return extractText(fileBytes);
    }

    // TODO(임시 구현): 실제 S3Client(GetObject)가 붙기 전까지, s3Key를 로컬 파일 경로로 취급해서
    // 바이트를 읽는다. ResumeService.upload()의 S3 저장 TODO가 실제 s3Key를 발급하도록
    // 바뀌는 시점에 여기도 S3Client 호출로 같이 교체해야 한다 — 둘이 짝을 이루는 TODO다.
    private byte[] fetchOriginalBytes(String s3Key) throws ResumeParsingFailedException {
        try {
            return Files.readAllBytes(Path.of(s3Key));
        } catch (IOException e) {
            // 원본을 못 읽는 건 "PDF가 손상됨"과는 다른 원인(파일 자체가 없음 등)이지만,
            // 호출부(ResumeParsingService)는 어차피 markFailed()로만 처리하므로 같은 예외로 합친다.
            throw new ResumeParsingFailedException("이력서 원본 파일을 읽을 수 없습니다.", e);
        }
    }

    private String extractText(byte[] fileBytes) throws ResumeParsingFailedException {
        if (!hasPdfMagicNumber(fileBytes)) {
            throw new ResumeParsingFailedException("PDF 매직넘버(%PDF-)가 올바르지 않습니다.");
        }
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // 텍스트 레이어 없이 이미지만 있는 스캔 PDF 등 — OCR은 스코프 밖(FR-01)이라
            // "추출은 됐지만 내용이 없는" 상태를 파싱 실패로 명시적으로 취급한다.
            if (text == null || text.isBlank()) {
                throw new ResumeParsingFailedException("PDF에서 추출할 텍스트가 없습니다.");
            }
            return text;
        } catch (InvalidPasswordException e) {
            // 비밀번호로 암호화된 PDF는 로그인 정보 없이는 원천적으로 열 수 없다 —
            // 재시도로 해결되지 않으므로 markFailed() 경로로 바로 보내고 사용자 재업로드를 유도한다.
            throw new ResumeParsingFailedException("암호로 보호된 PDF는 열 수 없습니다.", e);
        } catch (IOException e) {
            // 손상된 PDF, 지원하지 않는 포맷 등 그 외 파싱 불가 케이스의 공통 처리.
            throw new ResumeParsingFailedException("PDF 파싱 중 오류가 발생했습니다.", e);
        }
    }

    private boolean hasPdfMagicNumber(byte[] fileBytes) {
        if (fileBytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (fileBytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
