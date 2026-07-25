package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * originalFileName 재업로드 정책: 요청에 이름이 있으면 그 값으로 덮어쓰고, 없으면(null) 재사용하는
 * FAILED 슬롯의 기존 이름을 유지하며, 둘 다 없을 때만(신규 업로드이거나 기존 이름도 null인 레거시
 * 데이터) type 기준 폴백을 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class ResumeUploadPersistenceServiceTest {
    @Mock ResumeRepository resumeRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ResumeCapacityPolicy capacityPolicy;
    private ResumeUploadPersistenceService sut;

    @BeforeEach
    void setUp() {
        sut = new ResumeUploadPersistenceService(resumeRepository, eventPublisher, capacityPolicy);
    }

    @Test
    void newResumeUsesRequestedNameWhenPresent() {
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                1L, ResumeType.RESUME, ParseStatus.FAILED)).willReturn(Optional.empty());
        given(resumeRepository.save(any(Resume.class))).willAnswer(invocation -> invocation.getArgument(0));

        ResumeResponse result = sut.persist(1L, ResumeType.RESUME, "resumes/1/pending/id.pdf",
                "hash", "etag", "이력서_최종.pdf", "이력서.pdf", 1024L);

        assertThat(result.originalFileName()).isEqualTo("이력서_최종.pdf");
    }

    @Test
    void newResumeUsesFallbackWhenRequestedNameIsNull() {
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                1L, ResumeType.RESUME, ParseStatus.FAILED)).willReturn(Optional.empty());
        given(resumeRepository.save(any(Resume.class))).willAnswer(invocation -> invocation.getArgument(0));

        ResumeResponse result = sut.persist(1L, ResumeType.RESUME, "resumes/1/pending/id.pdf",
                "hash", "etag", null, "이력서.pdf", 1024L);

        assertThat(result.originalFileName()).isEqualTo("이력서.pdf");
    }

    @Test
    void reuploadOverwritesPreviousNameWhenRequestedNameIsPresent() {
        Resume failed = failedResume("old-name.pdf");
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                1L, ResumeType.RESUME, ParseStatus.FAILED)).willReturn(Optional.of(failed));

        ResumeResponse result = sut.persist(1L, ResumeType.RESUME, "resumes/1/pending/new.pdf",
                "hash", "etag", "new-name.pdf", "이력서.pdf", 2048L);

        assertThat(result.originalFileName()).isEqualTo("new-name.pdf");
        verify(resumeRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void reuploadPreservesPreviousNameWhenRequestedNameIsNull() {
        Resume failed = failedResume("이력서_최종.pdf");
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                1L, ResumeType.RESUME, ParseStatus.FAILED)).willReturn(Optional.of(failed));

        ResumeResponse result = sut.persist(1L, ResumeType.RESUME, "resumes/1/pending/new.pdf",
                "hash", "etag", null, "이력서.pdf", 2048L);

        assertThat(result.originalFileName()).isEqualTo("이력서_최종.pdf");
    }

    @Test
    void reuploadFallsBackWhenRequestedNameAndPreviousNameAreBothNull() {
        Resume failed = failedResume(null);
        given(resumeRepository.findFirstByUserIdAndTypeAndParseStatusAndDeletedAtIsNull(
                1L, ResumeType.RESUME, ParseStatus.FAILED)).willReturn(Optional.of(failed));

        ResumeResponse result = sut.persist(1L, ResumeType.RESUME, "resumes/1/pending/new.pdf",
                "hash", "etag", null, "이력서.pdf", 2048L);

        assertThat(result.originalFileName()).isEqualTo("이력서.pdf");
    }

    private Resume failedResume(String originalFileName) {
        Resume resume = new Resume(1L, ResumeType.RESUME, "resumes/1/pending/old.pdf",
                "old-hash", "old-etag", originalFileName, 512L);
        ReflectionTestUtils.setField(resume, "id", 99L);
        ReflectionTestUtils.setField(resume, "parseStatus", ParseStatus.FAILED);
        return resume;
    }
}
