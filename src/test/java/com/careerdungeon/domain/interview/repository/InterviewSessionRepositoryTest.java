package com.careerdungeon.domain.interview.repository;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InterviewSessionRepositoryTest {

    @Autowired
    InterviewSessionRepository interviewSessionRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("여러 세션 중 createdAt이 가장 최근인 세션을 반환한다")
    void findFirstByUserIdOrderByCreatedAtDescIdDesc_returnsMostRecentByCreatedAt() {
        User user = persistUser("user-1");
        InterviewSession oldest = session(user, Instant.parse("2026-07-20T00:00:00Z"));
        InterviewSession middle = session(user, Instant.parse("2026-07-22T00:00:00Z"));
        InterviewSession newest = session(user, Instant.parse("2026-07-25T00:00:00Z"));
        interviewSessionRepository.saveAllAndFlush(java.util.List.of(oldest, middle, newest));
        entityManager.clear();

        Optional<InterviewSession> result =
                interviewSessionRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(newest.getId());
    }

    @Test
    @DisplayName("createdAt이 같으면 id가 더 큰(나중에 생성된) 세션을 반환한다")
    void findFirstByUserIdOrderByCreatedAtDescIdDesc_tieBreaksByIdDescWhenCreatedAtEqual() {
        User user = persistUser("user-2");
        Instant sameInstant = Instant.parse("2026-07-25T12:00:00Z");
        InterviewSession first = session(user, sameInstant);
        InterviewSession second = session(user, sameInstant);
        interviewSessionRepository.saveAllAndFlush(java.util.List.of(first, second));
        entityManager.clear();

        Optional<InterviewSession> result =
                interviewSessionRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(second.getId());
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    @DisplayName("세션이 없는 사용자를 조회하면 빈 Optional을 반환한다")
    void findFirstByUserIdOrderByCreatedAtDescIdDesc_noSessions_returnsEmpty() {
        User userWithoutSessions = persistUser("user-3");

        Optional<InterviewSession> result = interviewSessionRepository
                .findFirstByUserIdOrderByCreatedAtDescIdDesc(userWithoutSessions.getId());

        assertThat(result).isEmpty();
    }

    private InterviewSession session(User user, Instant createdAt) {
        Resume resume = persistResume(user);
        PersonaConfig personaConfig = persistPersonaConfig();
        InterviewSession session = new InterviewSession(user, resume, personaConfig, "DB");
        ReflectionTestUtils.setField(session, "createdAt", createdAt);
        return session;
    }

    private User persistUser(String googleId) {
        User user = new User(googleId, googleId + "@example.com", "테스트유저");
        entityManager.persist(user);
        return user;
    }

    private Resume persistResume(User user) {
        Resume resume = new Resume(
                user.getId(), ResumeType.RESUME, "some/" + user.getId() + "-" + System.nanoTime() + ".pdf",
                "hash-" + System.nanoTime(), "etag");
        entityManager.persist(resume);
        return resume;
    }

    private PersonaConfig persistPersonaConfig() {
        PersonaConfig existing = entityManager
                .createQuery("select p from PersonaConfig p where p.level = 1", PersonaConfig.class)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        PersonaConfig personaConfig = new PersonaConfig(1, PersonaTone.LENIENT);
        entityManager.persist(personaConfig);
        return personaConfig;
    }
}
