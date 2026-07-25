package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.judgment.entity.AnswerScore;
import com.careerdungeon.domain.judgment.entity.JudgmentResult;
import com.careerdungeon.domain.judgment.exception.AnswerSubmissionException;
import com.careerdungeon.domain.judgment.llm.LlmEvaluationResponseAdapter;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
import com.careerdungeon.domain.progress.model.StageGaugePolicy;
import com.careerdungeon.domain.progress.service.StageProgressionService;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** interview가 전달한 LLM 원시 평가값부터 서버 채점·영속화·최종 판정을 담당한다. */
@Service
public class AnswerSubmissionService {

    private static final int MAXIMUM_QUESTION_SCORE = 20;
    private static final Set<Integer> INITIAL_TURNS = Set.of(1, 2, 3, 4);
    private static final Set<Integer> FINAL_TURNS = Set.of(1, 2, 3, 4, 5);

    private final AnswerScoreRepository answerScoreRepository;
    private final JudgmentResultRepository judgmentResultRepository;
    private final LlmEvaluationResponseAdapter evaluationResponseAdapter;
    private final JudgmentScoringService judgmentScoringService;
    private final StageProgressionService stageProgressionService;

    /** 원시 응답 어댑터, 채점기, 점수·판정 저장소와 진행도 판정 서비스를 주입받는다. */
    public AnswerSubmissionService(
            AnswerScoreRepository answerScoreRepository,
            JudgmentResultRepository judgmentResultRepository,
            LlmEvaluationResponseAdapter evaluationResponseAdapter,
            JudgmentScoringService judgmentScoringService,
            StageProgressionService stageProgressionService) {
        this.answerScoreRepository = answerScoreRepository;
        this.judgmentResultRepository = judgmentResultRepository;
        this.evaluationResponseAdapter = evaluationResponseAdapter;
        this.judgmentScoringService = judgmentScoringService;
        this.stageProgressionService = stageProgressionService;
    }

    /** interview가 받은 최초 LLM 원시 응답에 루브릭과 clamp를 적용한다. */
    public InitialJudgmentEvaluation scoreInitial(InitialEvaluationResponse rawResponse) {
        return judgmentScoringService.scoreInitial(evaluationResponseAdapter.toRawInitial(rawResponse));
    }

    /** 저장된 최초 확정 점수와 turn 5 LLM 원시 응답을 합쳐 최종 점수를 계산한다. */
    public FinalJudgmentEvaluation scoreFinal(
            InitialJudgmentEvaluation storedInitial,
            FinalEvaluationResponse rawResponse) {
        return judgmentScoringService.scoreFinal(
                storedInitial,
                evaluationResponseAdapter.toRawFinal(rawResponse));
    }

    /** 최초 확정 점수가 이미 존재하는지 확인해 중복 LLM 호출 전 차단에 사용한다. */
    public boolean hasInitialScores(Long sessionId) {
        return answerScoreRepository.existsBySession_Id(sessionId);
    }

    /** 최종 판정이 이미 존재하는지 확인해 중복 제출을 차단한다. */
    public boolean hasFinalResult(Long sessionId) {
        return judgmentResultRepository.existsBySession_Id(sessionId);
    }

    /** 최초 turn 1~4의 서버 확정 점수와 피드백을 영속화한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void persistInitialScores(
            InterviewSession session,
            InitialJudgmentEvaluation evaluation) {
        validateInitialEvaluation(evaluation);
        answerScoreRepository.saveAll(evaluation.evaluations().stream()
                .map(score -> AnswerScore.from(session, score))
                .toList());
    }

    /** 최초 turn 1~4의 확정 점수와 세션 레벨별 통과 기준을 최종 채점 모델로 복원한다. */
    @Transactional(readOnly = true)
    public InitialJudgmentEvaluation loadStoredInitialEvaluation(Long sessionId) {
        List<AnswerScore> stored = answerScoreRepository.findAllBySession_IdOrderByTurnAsc(sessionId);
        if (stored.size() != INITIAL_TURNS.size()
                || !stored.stream().map(AnswerScore::getTurn).collect(Collectors.toSet())
                        .equals(INITIAL_TURNS)) {
            throw error(
                    "JUDGMENT_INITIAL_SCORE_MISSING",
                    "최초 turn 1~4의 확정 점수가 모두 필요합니다.",
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
        int passingScore = StageGaugePolicy.from(stored.get(0).getCompletedStage()).passingScore();
        return new InitialJudgmentEvaluation(scores, totalScore, weakestTurn, false, passingScore);
    }

    /** turn 5 점수·최종 판정·진행도·해금·뱃지를 현재 반영 트랜잭션에 저장한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void persistFinalResult(
            InterviewSession session,
            FinalJudgmentEvaluation evaluation) {
        validateFinalEvaluation(session, evaluation);
        validateStoredInitialScores(session.getId(), evaluation);
        QuestionScore followUpScore = evaluation.evaluations().stream()
                .filter(score -> score.questionId() == 5)
                .findFirst()
                .orElseThrow(() -> error(
                        "JUDGMENT_FOLLOW_UP_SCORE_MISSING",
                        "꼬리질문 확정 점수가 누락되었습니다.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        answerScoreRepository.save(AnswerScore.from(session, followUpScore));
        judgmentResultRepository.save(JudgmentResult.from(session, evaluation));
        stageProgressionService.applyFinalScore(
                session.getUserId(),
                session.getPersonaConfig().getLevel(),
                evaluation.totalScore());
    }

    /** 최초 영속화 전에 turn 1~4 구성·점수·피드백·합계·최저점 불변식을 확인한다. */
    private void validateInitialEvaluation(InitialJudgmentEvaluation evaluation) {
        if (evaluation == null) {
            throw invalidInitialEvaluation("최초 확정 평가가 누락되었습니다.");
        }
        List<QuestionScore> scores = evaluation.evaluations();
        validateScoreSet(scores, INITIAL_TURNS, "최초 확정 평가");

        int totalScore = scores.stream().mapToInt(QuestionScore::score).sum();
        int minimumScore = scores.stream().mapToInt(QuestionScore::score).min().orElseThrow();
        boolean weakestMatches = scores.stream().anyMatch(score ->
                score.questionId() == evaluation.weakestQuestionId()
                        && score.score() == minimumScore);
        if (evaluation.totalScore() != totalScore || evaluation.passed() || !weakestMatches) {
            throw invalidInitialEvaluation("최초 확정 평가의 합계·합격 여부·최저점 문항이 일치하지 않습니다.");
        }
    }

    /** 최종 영속화 전에 turn 1~5 구성과 세션 레벨별 합계·합격 불변식을 다시 확인한다. */
    private void validateFinalEvaluation(
            InterviewSession session,
            FinalJudgmentEvaluation evaluation) {
        if (evaluation == null) {
            throw invalidFinalEvaluation("최종 확정 평가가 누락되었습니다.");
        }
        List<QuestionScore> scores = evaluation.evaluations();
        validateScoreSet(scores, FINAL_TURNS, "최종 확정 평가");

        int totalScore = scores.stream().mapToInt(QuestionScore::score).sum();
        int passingScore = StageGaugePolicy.from(session.getPersonaConfig().getLevel()).passingScore();
        if (evaluation.totalScore() != totalScore
                || evaluation.passingScore() != passingScore
                || evaluation.passed() != (totalScore >= passingScore)) {
            throw invalidFinalEvaluation("최종 확정 평가의 문항 합계·레벨별 통과 기준·합격 여부가 일치하지 않습니다.");
        }
    }

    /** 최종 평가의 turn 1~4가 현재 세션에 보존된 서버 확정 점수·피드백과 같은지 확인한다. */
    private void validateStoredInitialScores(
            Long sessionId,
            FinalJudgmentEvaluation evaluation) {
        List<QuestionScore> storedInitial = loadStoredInitialEvaluation(sessionId).evaluations().stream()
                .sorted(Comparator.comparingInt(QuestionScore::questionId))
                .toList();
        List<QuestionScore> evaluatedInitial = evaluation.evaluations().stream()
                .filter(score -> INITIAL_TURNS.contains(score.questionId()))
                .sorted(Comparator.comparingInt(QuestionScore::questionId))
                .toList();
        if (!storedInitial.equals(evaluatedInitial)) {
            throw error(
                    "JUDGMENT_INITIAL_SCORE_CHANGED",
                    "최종 판정의 최초 점수가 현재 세션의 확정 점수와 일치하지 않습니다.",
                    HttpStatus.CONFLICT);
        }
    }

    /** 단계별 필수 turn, 점수 범위와 영속화에 필요한 피드백을 공통 검증한다. */
    private void validateScoreSet(
            List<QuestionScore> scores,
            Set<Integer> expectedTurns,
            String stageName) {
        if (scores == null
                || scores.size() != expectedTurns.size()
                || scores.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalidEvaluation(stageName + "의 문항 수가 올바르지 않습니다.", expectedTurns);
        }
        Set<Integer> actualTurns = scores.stream()
                .map(QuestionScore::questionId)
                .collect(Collectors.toSet());
        boolean invalidScore = scores.stream().anyMatch(score ->
                score.score() < 0
                        || score.score() > MAXIMUM_QUESTION_SCORE
                        || score.feedback() == null
                        || score.feedback().isBlank());
        if (!actualTurns.equals(expectedTurns) || invalidScore) {
            throw invalidEvaluation(stageName + "의 turn·점수·피드백이 올바르지 않습니다.", expectedTurns);
        }
    }

    /** 최초/최종 단계에 맞는 내부 계약 예외를 선택한다. */
    private AnswerSubmissionException invalidEvaluation(String message, Set<Integer> expectedTurns) {
        return expectedTurns.equals(INITIAL_TURNS)
                ? invalidInitialEvaluation(message)
                : invalidFinalEvaluation(message);
    }

    /** 잘못 조립된 최초 확정 평가를 영속화하지 않도록 내부 계약 오류를 생성한다. */
    private AnswerSubmissionException invalidInitialEvaluation(String message) {
        return error("JUDGMENT_INITIAL_EVALUATION_INVALID", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 잘못 조립된 최종 확정 평가를 영속화하지 않도록 내부 계약 오류를 생성한다. */
    private AnswerSubmissionException invalidFinalEvaluation(String message) {
        return error("JUDGMENT_FINAL_EVALUATION_INVALID", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 공통 응답 계약으로 변환될 judgment 비즈니스 예외를 생성한다. */
    private AnswerSubmissionException error(String code, String message, HttpStatus status) {
        return new AnswerSubmissionException(code, message, status);
    }
}
