package com.careerdungeon.domain.resume.repository;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code resumes.extracted_text} 컬럼의 실제 DB 제약을 검증하는 테스트다.
 *
 * <p>원래 이 컬럼은 MySQL {@code TEXT}(최대 65,535바이트 ≈ 64KB)였는데, 파일 업로드 허용
 * 크기(10MB)에 가까운 텍스트를 저장하면 {@code DataIntegrityViolationException}
 * ({@code Data truncation: Data too long for column 'extracted_text'})으로 저장이
 * 실패하는 버그가 있었다(이슈 #193). {@code V28__change_resume_extracted_text_to_mediumtext.sql}로
 * {@code MEDIUMTEXT}(최대 16,777,215바이트 ≈ 16MB)로 넓혀서 고쳤고, 이 테스트는 그 수정이
 * 실제로 동작하는지 — 예전에는 실패했던 70,000자 텍스트가 이제는 정상 저장되는지 — 확인한다.
 *
 * <p>기본 테스트 프로필(H2, {@code src/test/resources/application-test.yml})은 애초에 이
 * 제약을 검증할 수 없다 — H2는 MySQL 호환 모드({@code MODE=MySQL})에서도 {@code TEXT}/
 * {@code MEDIUMTEXT}에 길이 제한을 두지 않기 때문에(H2는 둘 다 사실상 무제한 CLOB), 컬럼
 * 타입이 실제로 {@code MEDIUMTEXT}로 바뀌었는지는 H2로는 확인할 수 없고 실제 MySQL에
 * 붙어야만 확인할 수 있다.
 *
 * <p>그래서 이 테스트는 {@link com.careerdungeon.domain.resume.service.ResumeS3SmokeTest}와 동일한 방식으로, 로컬 실제 MySQL이 있을
 * 때만(-DrunResumeTextColumnSizeTest=true) 선택적으로 실행되도록 게이팅한다. 기본
 * {@code ./gradlew test}(H2)에는 포함되지 않으므로 CI나 다른 개발자의 빌드를 깨지 않는다.
 *
 * <p>실행 방법: 로컬에 {@code application-local.yml}에 설정된 MySQL이 떠 있는 상태에서
 * {@code ./gradlew test --tests "*.ResumeTextColumnSizeTest" -DrunResumeTextColumnSizeTest=true}
 */
@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "runResumeTextColumnSizeTest", matches = "true")
class ResumeTextColumnSizeTest {

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    @DisplayName("64KB를 초과하는 텍스트도 MEDIUMTEXT 컬럼에서는 저장에 성공한다")
    void savingSeventyThousandCharacterExtractedTextSucceedsOnMediumtextColumn() {
        Resume resume = saveResume();

        // 예전 TEXT 컬럼(65,535바이트 한계)에서는 저장이 실패했던 바로 그 크기.
        // MEDIUMTEXT(최대 16,777,215바이트)에서는 정상 저장돼야 한다.
        String largeText = "a".repeat(70_000);

        int updated = resumeRepository.updateParseResultIfActive(
                resume.getId(), largeText, ParseStatus.DONE, Instant.now());

        assertThat(updated).isEqualTo(1);
        assertThat(resumeRepository.findById(resume.getId()).orElseThrow().getExtractedText())
                .isEqualTo(largeText);
    }

    @Test
    @DisplayName("정상 크기의 텍스트는 저장에 성공한다")
    void savingNormalSizeExtractedTextSucceeds() {
        Resume resume = saveResume();
        String normalText = "a".repeat(1_000);

        int updated = resumeRepository.updateParseResultIfActive(
                resume.getId(), normalText, ParseStatus.DONE, Instant.now());

        assertThat(updated).isEqualTo(1);
        assertThat(resumeRepository.findById(resume.getId()).orElseThrow().getExtractedText())
                .isEqualTo(normalText);
    }

    private Resume saveResume() {
        User user = entityManager.persistFlushFind(
                new User("resume-text-column-size-" + Instant.now().toEpochMilli(),
                        "resume-text-column-size@example.com", "scratch"));
        return entityManager.persistFlushFind(
                new Resume(user.getId(), ResumeType.RESUME, "scratch/probe.txt", "hash", "etag"));
    }
}
