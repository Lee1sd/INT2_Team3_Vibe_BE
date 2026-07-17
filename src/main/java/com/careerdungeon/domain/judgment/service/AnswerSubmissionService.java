package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.interview.dto.InterviewQuestionResponse;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.interview.service.InterviewService;
import com.careerdungeon.domain.judgment.dto.AnswerEvaluationResponse;
import com.careerdungeon.domain.judgment.dto.AnswerSubmissionRequest;
import com.careerdungeon.domain.judgment.dto.AnswerSubmissionResponse;
import com.careerdungeon.domain.judgment.dto.FinalAnswerSubmissionResponse;
import com.careerdungeon.domain.judgment.dto.InitialAnswerSubmissionResponse;
import com.careerdungeon.domain.judgment.dto.NextTurnResponse;
import com.careerdungeon.domain.judgment.dto.SubmittedAnswerRequest;
import com.careerdungeon.domain.judgment.entity.AnswerScore;
import com.careerdungeon.domain.judgment.entity.JudgmentResult;
import com.careerdungeon.domain.judgment.exception.AnswerSubmissionException;
import com.careerdungeon.domain.judgment.llm.LlmEvaluationResponseAdapter;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentSessionRepository;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.progress.service.StageProgressionService;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** IS-002 답변 저장부터 채점·최종 판정·진행도 반영까지 한 트랜잭션으로 조정한다. */
@Service
public class AnswerSubmissionService {

    private static final Set<Integer> INITIAL_TURNS = Set.of(1, 2, 3);
    private static final Set<Integer> FINAL_TURNS = Set.of(4);

    private final JudgmentSessionRepository judgmentSessionRepository;
    private final MessageRepository messageRepository;
    private final QuestionRepository questionRepository;
    private final AnswerScoreRepository answerScoreRepository;
    private final JudgmentResultRepository judgmentResultRepository;
    private final LlmInvocationService llmInvocationService;
    private final LlmEvaluationResponseAdapter evaluationResponseAdapter;
    private final JudgmentScoringService judgmentScoringService;
    private final InterviewService interviewService;
    private final StageProgressionService stageProgressionService;

    /** 채점에 필요한 저장소·LLM 경계·진행도 서비스를 주입받는다. */
    public AnswerSubmissionService(
            JudgmentSessionRepository judgmentSessionRepository,
            MessageRepository messageRepository,
            QuestionRepository questionRepository,
            AnswerScoreRepository answerScoreRepository,
            JudgmentResultRepository judgmentResultRepository,
            LlmInvocationService llmInvocationService,
            LlmEvaluationResponseAdapter evaluationResponseAdapter,
            JudgmentScoringService judgmentScoringService,
            InterviewService interviewService,
            StageProgressionService stageProgressionService) {
        this.judgmentSessionRepository = judgmentSessionRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.answerScoreRepository = answerScoreRepository;
        this.judgmentResultRepository = judgmentResultRepository;
        this.llmInvocationService = llmInvocationService;
        this.evaluationResponseAdapter = evaluationResponseAdapter;
        this.judgmentScoringService = judgmentScoringService;
        this.interviewService = interviewService;
        this.stageProgressionService = stageProgressionService;
    }

    /** 세션 상태를 잠근 뒤 최초 채점과 최종 판정 중 실행할 단계를 자동으로 선택한다. */
    @Transactional
    public AnswerSubmissionResponse submit(
            Long userId,
            Long sessionId,
            AnswerSubmissionRequest request) {
        if (userId == null) {
            throw error("JUDGMENT_UNAUTHENTICATED", "인증 정보가 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        if (request == null || request.answers() == null) {
            throw error("JUDGMENT_ANSWERS_REQUIRED", "answers는 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        InterviewSession session = judgmentSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> error(
                        "INTERVIEW_SESSION_NOT_FOUND",
                        "면접 세션을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        validateOwner(session, userId);

        return switch (session.getStatus()) {
            case IN_PROGRESS -> submitInitial(session, request.answers());
            case AWAITING_FOLLOWUP -> submitFinal(session, request.answers());
            case COMPLETED -> throw error(
                    "JUDGMENT_ALREADY_COMPLETED",
                    "이미 최종 판정이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        };
    }

    /** 최초 turn 1~3 답변을 한 번만 저장하고 서버 확정 점수와 꼬리질문을 만든다. */
    private InitialAnswerSubmissionResponse submitInitial(
            InterviewSession session,
            List<SubmittedAnswerRequest> answers) {
        if (answerScoreRepository.existsBySession_Id(session.getId())) {
            throw error(
                    "JUDGMENT_INITIAL_ALREADY_SCORED",
                    "이미 최초 채점이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        }

        List<ResolvedAnswer> resolvedAnswers = resolveAnswers(session, answers, INITIAL_TURNS);
        saveAnswers(session, resolvedAnswers);
        List<QuestionAnswerPair> pairs = resolvedAnswers.stream()
                .map(this::toQuestionAnswerPair)
                .toList();

        InitialEvaluationResponse llmResponse = llmInvocationService.evaluateInitialAnswers(
                EvaluationRequest.initial(
                        pairs,
                        session.getPersonaConfig().getTone().name(),
                        session.getUser().getName()));
        InitialJudgmentEvaluation evaluation = judgmentScoringService.scoreInitial(
                evaluationResponseAdapter.toRawInitial(llmResponse));
        answerScoreRepository.saveAll(evaluation.evaluations().stream()
                .map(score -> AnswerScore.from(session, score))
                .toList());

        // PR #68의 꼬리질문 생성·저장 책임은 interview에 유지하고 확정 점수만 전달한다.
        InterviewQuestionResponse followUp = interviewService.generateFollowUpQuestionFromScoredInitial(
                session.getUserId(),
                session.getId(),
                evaluation);
        long weakestQuestionId = evaluation.weakestQuestionId();

        return new InitialAnswerSubmissionResponse(
                toInitialResponses(evaluation.evaluations()),
                evaluation.totalScore(),
                weakestQuestionId,
                evaluation.passed(),
                NextTurnResponse.followUp(weakestQuestionId, followUp.question()));
    }

    /** turn 4만 신규 채점하고 최초 확정 점수와 합산해 판정·진행도·상태를 함께 반영한다. */
    private FinalAnswerSubmissionResponse submitFinal(
            InterviewSession session,
            List<SubmittedAnswerRequest> answers) {
        if (judgmentResultRepository.existsBySession_Id(session.getId())) {
            throw error(
                    "JUDGMENT_ALREADY_COMPLETED",
                    "이미 최종 판정이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        }

        List<ResolvedAnswer> resolvedAnswers = resolveAnswers(session, answers, FINAL_TURNS);
        saveAnswers(session, resolvedAnswers);
        QuestionAnswerPair followUpPair = toQuestionAnswerPair(resolvedAnswers.get(0));
        InitialJudgmentEvaluation storedInitial = loadStoredInitialEvaluation(session.getId());
        List<PreviousEvaluationContext> previousContexts = buildPreviousContexts(
                session.getId(),
                storedInitial.evaluations());

        FinalEvaluationResponse llmResponse = llmInvocationService.evaluateFinalAnswers(
                EvaluationRequest.finalEvaluation(
                        List.of(followUpPair),
                        previousContexts,
                        session.getPersonaConfig().getTone().name(),
                        session.getUser().getName()));
        FinalJudgmentEvaluation evaluation = judgmentScoringService.scoreFinal(
                storedInitial,
                evaluationResponseAdapter.toRawFinal(llmResponse));

        QuestionScore followUpScore = evaluation.evaluations().stream()
                .filter(score -> score.questionId() == 4)
                .findFirst()
                .orElseThrow(() -> error(
                        "JUDGMENT_FOLLOW_UP_SCORE_MISSING",
                        "꼬리질문 확정 점수가 누락되었습니다.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        answerScoreRepository.save(AnswerScore.from(session, followUpScore));
        judgmentResultRepository.save(JudgmentResult.from(session, evaluation));

        // 판정 저장, 게이지·해금·뱃지, COMPLETED 전이는 모두 현재 트랜잭션에 참여한다.
        stageProgressionService.applyFinalScore(
                session.getUserId(),
                session.getPersonaConfig().getLevel(),
                evaluation.totalScore());
        session.complete();

        return new FinalAnswerSubmissionResponse(
                toFinalResponses(evaluation.evaluations()),
                evaluation.totalScore(),
                evaluation.passed(),
                evaluation.overallFeedback(),
                null);
    }

    /** 외부 questionId를 세션 내부 turn으로 해석하고 상태별 정확한 문항 집합인지 검증한다. */
    private List<ResolvedAnswer> resolveAnswers(
            InterviewSession session,
            List<SubmittedAnswerRequest> answers,
            Set<Integer> expectedTurns) {
        if (answers.size() != expectedTurns.size()) {
            throw invalidQuestionSet(expectedTurns);
        }

        Set<Integer> seenTurns = new HashSet<>();
        List<ResolvedAnswer> resolved = new ArrayList<>();
        for (SubmittedAnswerRequest answer : answers) {
            if (answer == null || answer.questionId() == null
                    || answer.questionId() < Integer.MIN_VALUE
                    || answer.questionId() > Integer.MAX_VALUE
                    || answer.answerText() == null
                    || answer.answerText().isBlank()) {
                throw error(
                        "JUDGMENT_ANSWER_INVALID",
                        "questionId와 answerText는 필수입니다.",
                        HttpStatus.BAD_REQUEST);
            }
            int turn = answer.questionId().intValue();
            if (!expectedTurns.contains(turn) || !seenTurns.add(turn)) {
                throw invalidQuestionSet(expectedTurns);
            }
            Message questionMessage = findMessage(session.getId(), MessageRole.QUESTION, turn);
            resolved.add(new ResolvedAnswer(questionMessage, answer.answerText()));
        }
        if (!seenTurns.equals(expectedTurns)) {
            throw invalidQuestionSet(expectedTurns);
        }
        resolved.sort(Comparator.comparingInt(value -> value.questionMessage().getTurn()));
        return List.copyOf(resolved);
    }

    /** 같은 세션·turn 답변이 이미 있으면 중복 LLM 호출 전에 거부하고 새 답변을 저장한다. */
    private void saveAnswers(InterviewSession session, List<ResolvedAnswer> resolvedAnswers) {
        for (ResolvedAnswer resolved : resolvedAnswers) {
            int turn = resolved.questionMessage().getTurn();
            if (messageRepository.existsBySession_IdAndRoleAndTurn(
                    session.getId(), MessageRole.ANSWER, turn)) {
                throw error(
                        "JUDGMENT_ANSWER_ALREADY_SUBMITTED",
                        "이미 제출된 답변이 있습니다: questionId=" + turn,
                        HttpStatus.CONFLICT);
            }
        }
        messageRepository.saveAll(resolvedAnswers.stream()
                .map(resolved -> new Message(
                        session,
                        MessageRole.ANSWER,
                        resolved.answerText(),
                        resolved.questionMessage().getTurn()))
                .toList());
    }

    /** 저장된 질문 본문·모범답변과 제출 답변을 LLM 평가 입력으로 조립한다. */
    private QuestionAnswerPair toQuestionAnswerPair(ResolvedAnswer resolved) {
        Message questionMessage = resolved.questionMessage();
        Question question = questionRepository.findById(questionMessage.getId())
                .orElseThrow(() -> error(
                        "JUDGMENT_EXPECTED_ANSWER_NOT_FOUND",
                        "질문 모범답안을 찾을 수 없습니다: questionId=" + questionMessage.getTurn(),
                        HttpStatus.INTERNAL_SERVER_ERROR));
        return new QuestionAnswerPair(
                questionMessage.getTurn(),
                questionMessage.getContent(),
                resolved.answerText(),
                question.getExpectedAnswer());
    }

    /** 최초 turn 1~3의 확정 점수와 피드백을 최종 채점용 불변 모델로 복원한다. */
    private InitialJudgmentEvaluation loadStoredInitialEvaluation(Long sessionId) {
        List<AnswerScore> stored = answerScoreRepository.findAllBySession_IdOrderByTurnAsc(sessionId);
        if (stored.size() != INITIAL_TURNS.size()
                || !stored.stream().map(AnswerScore::getTurn).collect(Collectors.toSet())
                        .equals(INITIAL_TURNS)) {
            throw error(
                    "JUDGMENT_INITIAL_SCORE_MISSING",
                    "최초 turn 1~3의 확정 점수가 모두 필요합니다.",
                    HttpStatus.CONFLICT);
        }

        List<QuestionScore> scores = stored.stream()
                .map(score -> new QuestionScore(score.getTurn(), score.getScore(), score.getFeedback()))
                .toList();
        int totalScore = scores.stream().mapToInt(QuestionScore::score).sum();
        int weakestTurn = scores.stream()
                .min(Comparator.comparingInt(QuestionScore::score))
                .map(QuestionScore::questionId)
                .orElseThrow();
        return new InitialJudgmentEvaluation(scores, totalScore, weakestTurn, false);
    }

    /** 최초 질문·답변·확정 점수·피드백을 재채점 금지 읽기 전용 컨텍스트로 조립한다. */
    private List<PreviousEvaluationContext> buildPreviousContexts(
            Long sessionId,
            List<QuestionScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingInt(QuestionScore::questionId))
                .map(score -> new PreviousEvaluationContext(
                        score.questionId(),
                        findMessage(sessionId, MessageRole.QUESTION, score.questionId()).getContent(),
                        findMessage(sessionId, MessageRole.ANSWER, score.questionId()).getContent(),
                        score.score(),
                        score.feedback()))
                .toList();
    }

    /** 최초 응답은 세 문항의 확정 피드백을 모두 포함한다. */
    private List<AnswerEvaluationResponse> toInitialResponses(List<QuestionScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingInt(QuestionScore::questionId))
                .map(score -> new AnswerEvaluationResponse(
                        score.questionId(),
                        score.score(),
                        score.feedback()))
                .toList();
    }

    /** 최종 응답은 기존 1~3 점수와 신규 turn 4 점수·피드백을 API 명세 순서로 반환한다. */
    private List<AnswerEvaluationResponse> toFinalResponses(List<QuestionScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingInt(QuestionScore::questionId))
                .map(score -> new AnswerEvaluationResponse(
                        score.questionId(),
                        score.score(),
                        score.questionId() == 4 ? score.feedback() : null))
                .toList();
    }

    /** 세션 소유자가 아닌 사용자의 답변 제출을 차단한다. */
    private void validateOwner(InterviewSession session, Long userId) {
        if (!session.getUserId().equals(userId)) {
            throw error(
                    "INTERVIEW_SESSION_FORBIDDEN",
                    "본인의 면접 세션만 사용할 수 있습니다.",
                    HttpStatus.FORBIDDEN);
        }
    }

    /** 세션·역할·turn으로 메시지를 조회하고 누락을 명시적 계약 오류로 변환한다. */
    private Message findMessage(Long sessionId, MessageRole role, int turn) {
        return messageRepository.findBySession_IdAndRoleAndTurn(sessionId, role, turn)
                .orElseThrow(() -> error(
                        "JUDGMENT_MESSAGE_NOT_FOUND",
                        "면접 메시지를 찾을 수 없습니다: role=" + role + ", questionId=" + turn,
                        HttpStatus.BAD_REQUEST));
    }

    /** 상태별 필수 questionId 집합 위반을 400 예외로 만든다. */
    private AnswerSubmissionException invalidQuestionSet(Set<Integer> expectedTurns) {
        return error(
                "JUDGMENT_QUESTION_SET_INVALID",
                "답변 questionId 구성은 " + expectedTurns + "여야 합니다.",
                HttpStatus.BAD_REQUEST);
    }

    /** 공통 응답 계약으로 변환될 judgment 비즈니스 예외를 생성한다. */
    private AnswerSubmissionException error(String code, String message, HttpStatus status) {
        return new AnswerSubmissionException(code, message, status);
    }

    /** 검증이 끝난 질문 메시지와 사용자 답변을 함께 운반한다. */
    private record ResolvedAnswer(Message questionMessage, String answerText) {
    }
}
