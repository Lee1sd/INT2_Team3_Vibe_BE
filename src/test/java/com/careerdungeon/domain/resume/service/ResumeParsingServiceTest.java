package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.parser.ResumeTextExtractor;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResumeParsingServiceTest {
    @Mock ResumeRepository repository;
    @Mock ResumeTextExtractor extractor;
    @Mock ResumeFileCleanupService cleanup;
    @Mock ResumeFileStorage storage;
    @Mock ResumeParsingPersistenceService parsingPersistence;
    private ResumeParsingService sut;

    @BeforeEach
    void setUp() {
        sut = new ResumeParsingService(repository, extractor,
                new ResumePiiMaskingService(), cleanup, storage, parsingPersistence);
    }

    @Test
    void downloadsFromS3MasksAndStoresExtractedTextThenDeletesObject() {
        String key = "resumes/1/pending/id.txt";
        byte[] bytes = "user@example.com".getBytes(StandardCharsets.UTF_8);
        Resume resume = new Resume(1L, ResumeType.RESUME, key, "hash");
        given(repository.findByIdAndDeletedAtIsNull(501L)).willReturn(Optional.of(resume));
        given(storage.download(key)).willReturn(bytes);
        given(extractor.extract(key, bytes)).willReturn("contact user@example.com");

        sut.parse(501L);

        verify(parsingPersistence).markDoneIfActive(eq(501L), eq("contact [EMAIL]"), any());
        verify(storage).delete(key);
    }

    @Test
    void deleteFailureRegistersRetryTask() {
        String key = "resumes/1/pending/id.txt";
        byte[] bytes = "text".getBytes();
        Resume resume = new Resume(1L, ResumeType.RESUME, key, "hash");
        given(repository.findByIdAndDeletedAtIsNull(501L)).willReturn(Optional.of(resume));
        given(storage.download(key)).willReturn(bytes);
        given(extractor.extract(key, bytes)).willReturn("text");
        org.mockito.BDDMockito.willThrow(new RuntimeException("S3 down")).given(storage).delete(key);

        sut.parse(501L);

        verify(cleanup).enqueue(501L, key);
    }
}
