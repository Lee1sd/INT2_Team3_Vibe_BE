package com.careerdungeon.domain.interview.repository;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.InterviewSessionStatus;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class InterviewPersistenceRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    PersonaConfigRepository personaConfigRepository;

    @Autowired
    InterviewSessionRepository interviewSessionRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Test
    @DisplayName("InterviewSession, Message, Question을 ERD/ADR 키 구조로 저장한다")
    void savesInterviewMessageAndQuestion() {
        InterviewSession session = createSession("DB");
        Message message = messageRepository.saveAndFlush(
                new Message(session, MessageRole.QUESTION, "인덱스를 설명해 주세요.", 1));

        Question question = questionRepository.saveAndFlush(new Question(message, "인덱스는 조회 성능을 높이는 자료구조입니다."));

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(message.getSessionId()).isEqualTo(session.getId());
        assertThat(question.getMessageId()).isEqualTo(message.getId());
        assertThat(question.getExpectedAnswer()).contains("인덱스");
    }

    @Test
    @DisplayName("같은 session_id, role, turn 메시지는 UNIQUE 제약으로 중복 저장할 수 없다")
    void duplicateSessionRoleTurnViolatesUniqueConstraint() {
        InterviewSession session = createSession("DB");
        messageRepository.saveAndFlush(new Message(session, MessageRole.QUESTION, "첫 번째 질문", 1));

        assertThatThrownBy(() -> messageRepository.saveAndFlush(
                        new Message(session, MessageRole.QUESTION, "중복 질문", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 session_id와 turn이라도 role이 다르면 질문과 답변을 함께 저장할 수 있다")
    void sameTurnAllowsDifferentRoles() {
        InterviewSession session = createSession("DB");

        messageRepository.saveAndFlush(new Message(session, MessageRole.QUESTION, "질문", 1));
        Message answer = messageRepository.saveAndFlush(new Message(session, MessageRole.ANSWER, "답변", 1));

        assertThat(answer.getId()).isNotNull();
    }

    private InterviewSession createSession(String selectedKeyword) {
        User user = userRepository.saveAndFlush(new User(
                "interview-" + selectedKeyword,
                selectedKeyword.toLowerCase() + "@example.com",
                "tester"));
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/" + selectedKeyword + ".pdf",
                "hash-" + selectedKeyword));
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.LENIENT));
        return interviewSessionRepository.saveAndFlush(new InterviewSession(user, resume, personaConfig, selectedKeyword));
    }
}
