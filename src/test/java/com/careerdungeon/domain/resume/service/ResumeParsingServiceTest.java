package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.parser.PdfBoxResumeTextExtractor;
import com.careerdungeon.domain.resume.parser.PlainTextResumeTextExtractor;
import com.careerdungeon.domain.resume.parser.RoutingResumeTextExtractor;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 이 테스트는 파싱 로직(업로드→저장→파싱→성공/실패 처리)을 서비스 레벨에서 검증합니다.
 * Controller HTTP 요청, 실제 트랜잭션 커밋, {@code @TransactionalEventListener}, {@code @Async}
 * 실행 여부는 각각 {@code ResumeControllerTest}, {@code ResumeParsingServiceAsyncTest}에서 별도로 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ResumeParsingServiceTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"txt", "md"})
    @DisplayName("TXT/MD 원문을 추출해 DONE으로 전이하고 임시 원본을 삭제한다")
    void parse_plainTextFiles_marksDoneAndDeletesOriginal(String extension) throws Exception {
        String content = "# 백엔드 경력\n* Spring Boot";
        Path file = tempDir.resolve("resume." + extension);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        Resume resume = new Resume(1L, ResumeType.RESUME, file.toString(), "hash");
        given(resumeRepository.findByIdAndDeletedAtIsNull(501L)).willReturn(Optional.of(resume));

        createService().parse(501L);

        verify(resumeRepository).updateParseResultIfActive(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.eq(ParseStatus.DONE),
                any());
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("파싱 도중 삭제되면 늦게 끝난 추출 결과는 활성 Resume 조건부 업데이트로만 저장을 시도한다")
    void parse_deletedWhileExtracting_usesConditionalActiveUpdate() throws Exception {
        Path file = tempDir.resolve("resume.txt");
        Files.writeString(file, "파싱 대상", StandardCharsets.UTF_8);
        Resume resume = new Resume(1L, ResumeType.RESUME, file.toString(), "hash");
        given(resumeRepository.findByIdAndDeletedAtIsNull(501L)).willReturn(Optional.of(resume));

        ResumeParsingService service = new ResumeParsingService(resumeRepository, s3Key -> {
            resume.delete();
            return "삭제보다 늦게 끝난 파싱 결과";
        });

        service.parse(501L);

        verify(resumeRepository).updateParseResultIfActive(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.eq("삭제보다 늦게 끝난 파싱 결과"),
                org.mockito.ArgumentMatchers.eq(ParseStatus.DONE),
                any());
        assertThat(resume.getDeletedAt()).isNotNull();
        assertThat(resume.getExtractedText()).isNull();
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("빈 TXT는 FAILED로 전이하고 임시 원본을 삭제한다")
    void parse_blankTxt_marksFailedAndDeletesOriginal() throws Exception {
        Path file = tempDir.resolve("resume.txt");
        Files.writeString(file, " \n\t", StandardCharsets.UTF_8);
        Resume resume = new Resume(1L, ResumeType.RESUME, file.toString(), "hash");
        given(resumeRepository.findByIdAndDeletedAtIsNull(501L)).willReturn(Optional.of(resume));

        createService().parse(501L);

        verify(resumeRepository).updateParseResultIfActive(501L, null, ParseStatus.FAILED, null);
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("순수 한글 텍스트의 이름만 PDF로 바꾼 파일은 FAILED로 전이하고 원본을 삭제한다")
    void parse_plainTextRenamedToPdf_marksFailedAndDeletesOriginal() throws Exception {
        Path file = tempDir.resolve("test123.pdf");
        Files.writeString(file, "이것은 테스트입니다", StandardCharsets.UTF_8);
        Resume resume = new Resume(1L, ResumeType.RESUME, file.toString(), "hash");
        given(resumeRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(resume));

        createService().parse(7L);

        verify(resumeRepository).updateParseResultIfActive(7L, null, ParseStatus.FAILED, null);
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("순수 텍스트 PDF 업로드는 PROCESSING 응답 후 비동기 파싱에서 FAILED가 된다")
    void uploadAndParse_plainTextRenamedToPdf_transitionsFromProcessingToFailed() {
        given(resumeRepository.countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
                1L, ResumeType.RESUME, Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED))).willReturn(0L);
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                1L, ResumeType.RESUME, ParseStatus.FAILED)).willReturn(Optional.empty());
        given(resumeRepository.save(any(Resume.class))).willAnswer(invocation -> {
            Resume saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        ResumeService uploadService = new ResumeService(resumeRepository, eventPublisher);
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file",
                "test123.pdf",
                "application/pdf",
                "이것은 테스트입니다".getBytes(StandardCharsets.UTF_8));

        ResumeResponse uploadResponse = uploadService.upload(1L, ResumeType.RESUME, fakePdf);
        // Mockito repository에는 실제 저장소가 없으므로 save 호출 인자를 다시 얻는다.
        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Resume uploaded = captor.getValue();
        given(resumeRepository.findByIdAndDeletedAtIsNull(7L)).willReturn(Optional.of(uploaded));

        createService().parse(7L);

        assertThat(uploadResponse.resumeId()).isEqualTo(7L);
        assertThat(uploadResponse.parseStatus()).isEqualTo(ParseStatus.PROCESSING);
        verify(resumeRepository).updateParseResultIfActive(7L, null, ParseStatus.FAILED, null);
        assertThat(Path.of(uploaded.getS3Key())).doesNotExist();
    }

    private ResumeParsingService createService() {
        RoutingResumeTextExtractor extractor = new RoutingResumeTextExtractor(
                new PdfBoxResumeTextExtractor(), new PlainTextResumeTextExtractor());
        return new ResumeParsingService(resumeRepository, extractor);
    }
}
