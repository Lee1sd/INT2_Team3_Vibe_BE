package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.dto.InterviewQuestionResponse;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import com.careerdungeon.global.exception.BusinessException;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class InterviewService {

    private static final Set<String> MVP_ALLOWED_KEYWORDS = Set.of("DB", "보안");

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final PersonaConfigRepository personaConfigRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final MessageRepository messageRepository;
    private final QuestionRepository questionRepository;
    private final UserUnlockStatusRepository userUnlockStatusRepository;
    private final QuestionGenerationPromptProvider promptProvider;
    private final LlmInvocationService llmInvocationService;

    public InterviewService(
            UserRepository userRepository,
            ResumeRepository resumeRepository,
            PersonaConfigRepository personaConfigRepository,
            InterviewSessionRepository interviewSessionRepository,
            MessageRepository messageRepository,
            QuestionRepository questionRepository,
            UserUnlockStatusRepository userUnlockStatusRepository,
            QuestionGenerationPromptProvider promptProvider,
            LlmInvocationService llmInvocationService) {
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.personaConfigRepository = personaConfigRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.userUnlockStatusRepository = userUnlockStatusRepository;
        this.promptProvider = promptProvider;
        this.llmInvocationService = llmInvocationService;
    }

    @Transactional
    public InterviewCreateResponse createInterview(Long userId, InterviewCreateRequest request) {
        String keyword = validateKeyword(request.keyword());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        Resume resume = findOwnedResume(userId, request.resumeId());
        PersonaConfig personaConfig = personaConfigRepository.findById(request.interviewerId())
                .orElseThrow(() -> new BusinessException(
                        "PERSONA_NOT_FOUND",
                        "면접관 설정을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        validateInterviewerUnlocked(userId, personaConfig);

        InterviewSession session = interviewSessionRepository.save(
                new InterviewSession(user, resume, personaConfig, keyword));

        QuestionGenerationRequest llmRequest = new QuestionGenerationRequest(
                resume.getExtractedText(),
                keyword,
                personaConfig.getTone().name(),
                user.getName());
        promptProvider.prompt(llmRequest);
        QuestionGenerationResponse llmResponse = llmInvocationService.generateQuestions(llmRequest);

        List<InterviewQuestionResponse> questions = llmResponse.questions().stream()
                .map(generatedQuestion -> saveQuestion(session, generatedQuestion))
                .toList();

        return new InterviewCreateResponse(session.getId(), session.getStatus().name(), questions);
    }

    private String validateKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw invalidKeyword();
        }
        String normalizedKeyword = keyword.strip();
        if (!MVP_ALLOWED_KEYWORDS.contains(normalizedKeyword)) {
            throw invalidKeyword();
        }
        return normalizedKeyword;
    }

    private void validateInterviewerUnlocked(Long userId, PersonaConfig personaConfig) {
        UserUnlockStatus unlockStatus = userUnlockStatusRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_UNLOCK_STATUS_NOT_FOUND",
                        "사용자 진행도 상태를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        if (personaConfig.getLevel() > unlockStatus.getUnlockedLevel()) {
            throw new BusinessException(
                    "INTERVIEWER_LOCKED",
                    "아직 해금되지 않은 면접관입니다.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private BusinessException invalidKeyword() {
        return new BusinessException(
                "INTERVIEW_KEYWORD_INVALID",
                "면접 키워드는 MVP 허용 목록(DB, 보안) 중 하나여야 합니다.",
                HttpStatus.BAD_REQUEST);
    }

    private Resume findOwnedResume(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new BusinessException(
                        "RESUME_NOT_FOUND",
                        "이력서를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(
                    "RESUME_FORBIDDEN",
                    "본인의 이력서만 사용할 수 있습니다.",
                    HttpStatus.FORBIDDEN);
        }
        if (resume.getType() != ResumeType.RESUME) {
            throw new BusinessException(
                    "RESUME_TYPE_INVALID",
                    "면접 세션에는 RESUME 타입 이력서만 사용할 수 있습니다.",
                    HttpStatus.BAD_REQUEST);
        }
        if (resume.getExtractedText() == null || resume.getExtractedText().isBlank()) {
            throw new BusinessException(
                    "RESUME_TEXT_EMPTY",
                    "이력서 추출 텍스트가 없습니다.",
                    HttpStatus.BAD_REQUEST);
        }
        return resume;
    }

    private InterviewQuestionResponse saveQuestion(InterviewSession session, GeneratedQuestion generatedQuestion) {
        Message message = messageRepository.save(new Message(
                session,
                MessageRole.QUESTION,
                generatedQuestion.questionText(),
                generatedQuestion.turn()));
        questionRepository.save(new Question(message, generatedQuestion.expectedAnswer()));
        return new InterviewQuestionResponse(generatedQuestion.turn(), generatedQuestion.questionText());
    }
}
