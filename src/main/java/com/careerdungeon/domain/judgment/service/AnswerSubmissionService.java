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

    private static final Set<Integer> INITIAL_TURNS = Set.of(1, 2, 3);

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

    /** 저장된 최초 확정 점수와 turn 4 LLM 원시 응답을 합쳐 최종 점수를 계산한다. */
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

    /** 최초 turn 1~3의 서버 확정 점수와 피드백을 영속화한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void persistInitialScores(
            InterviewSession session,
            InitialJudgmentEvaluation evaluation) {
        answerScoreRepository.saveAll(evaluation.evaluations().stream()
                .map(score -> AnswerScore.from(session, score))
                .toList());
    }

    /** 최초 turn 1~3의 확정 점수와 피드백을 최종 채점용 불변 모델로 복원한다. */
    public InitialJudgmentEvaluation loadStoredInitialEvaluation(Long sessionId) {
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

    /** turn 4 점수·최종 판정·진행도·해금·뱃지를 현재 반영 트랜잭션에 저장한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void persistFinalResult(
            InterviewSession session,
            FinalJudgmentEvaluation evaluation) {
        QuestionScore followUpScore = evaluation.evaluations().stream()
                .filter(score -> score.questionId() == 4)
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

    /** 공통 응답 계약으로 변환될 judgment 비즈니스 예외를 생성한다. */
    private AnswerSubmissionException error(String code, String message, HttpStatus status) {
        return new AnswerSubmissionException(code, message, status);
    }
}
