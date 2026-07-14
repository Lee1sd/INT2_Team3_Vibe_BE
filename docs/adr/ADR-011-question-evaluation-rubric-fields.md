# ADR-011 — global.llm 최종 채점 계약을 4문항 전체 구조로 정합화 + 루브릭 필드 추가

- 상태: 제안
- 작성자: 김한비
- 작성일: 2026-07-13
- 관련 이슈/PR: #24, PR #18, ADR-008, ADR-009

## 배경

최용성님 확인 결과, `domain.judgment`(PR #18)의 `EvaluationLlmClient.evaluateFinal`은
이미 questionId `{1,2,3,4}` 전체를 요구하도록 완성되어 있다 — 입력 `EvaluationRequest`는
4개의 완전한 `QuestionAnswerPair`(`questionId`, `questionText`, `userAnswer`,
`expectedAnswer`)를 받는다.

반면 실제 코드를 확인해 보니 `global.llm` 쪽은 여전히 옛날 3문항 구조를 쓰고 있었다:

- `EvaluationRequest.followUp(followUpPair, tone, name, retainedTurns)`
  (`global/llm/dto/EvaluationRequest.java:32-35`) — `questionAnswerPairs`엔 꼬리질문
  1개만 담고, 나머지는 turn 번호만 있는 `Set<Integer> retainedTurns`로 전달한다
  (질문 텍스트·답변·모범답안 없음).
- `LlmInvocationService.evaluateFinalAnswers`(`global/llm/LlmInvocationService.java:98-107`)도
  `questionAnswerPairs().get(0)`(꼬리질문 1건)만 사용한다.
- `MockLlmClient.buildFinalEvaluations`(`global/llm/mock/MockLlmClient.java:84-91`)는
  `retainedTurns`의 각 turn에 대해 **재평가 없이 고정 score + 빈 feedback**만 채우고
  꼬리질문 1개만 실제로 평가한다 — `evaluations` 총 개수가 2(retained)+1(followUp)=**3개**,
  25점×3=최대 **75점**이 되어 FR-04/API 명세(4문항·100점)와도, judgment가 요구하는
  4문항 전체 입력과도 어긋난다.

즉 어제(2026-07-13) 반영한 것은 FR-04/api-spec/ERD **문서** 정정과 judgment 도메인
(PR #18)의 4문항 계약뿐이었고, `global.llm`의 실제 `EvaluationRequest`/
`LlmInvocationService`/`MockLlmClient` 코드는 3문항 구조 그대로 남아 있었다. 이 ADR은
그 간극을 좁히는 결정을 남긴다.

**기획서 §3 원문 근거**: 최종 채점은 최저점 문항(꼬리질문이 발동된 문항)을 대체하지
않는다. 최초 3문항 전부를 재평가하고 꼬리질문 1개를 더해 4문항 전체를 유지·합산하는
방식이 맞다 — "4문항 합산 100점 만점"이 이를 뒷받침한다(3문항×25=75점이 아니라
4문항×25=100점이어야 하는 이유). 예를 들어 turn 2가 최저점이라 꼬리질문(turn 4)이
발동돼도, 최종 응답에는 turn 2가 빠지고 turn 4로 대체되는 것이 아니라 turn {1,2,3,4}
전체가 남아 각자 재평가된 점수로 합산된다 — "교체"가 아니라 "보강". 이 혼동이 실제로
한 번 발생했으므로(대화 중 turn 2 대체 여부 질문) 여기 명시해 재발을 막는다.

## 결정

- `global.llm`의 최종 채점 계약을 "꼬리질문 1개 + turn 번호만 있는 retainedTurns" 구조에서
  **최초 3문항 + 꼬리질문 1개를 합친 4개 전체 `QuestionAnswerPair`(질문·답변·모범답안
  포함)를 그대로 전달하는 구조**로 변경한다 — `domain.judgment.llm.dto.EvaluationRequest`가
  이미 요구하는 형태와 동일하게 맞춘다.
  - `EvaluationRequest`(global.llm)의 `followUp(...)` 팩토리를 4개 `QuestionAnswerPair`를
    받는 형태로 변경(또는 대체)하고, turn 번호만 담던 `retainedTurns: Set<Integer>`는
    제거하거나 검증 전용 파생값으로만 남긴다.
  - `LlmInvocationService.evaluateFinalAnswers`, `MockLlmClient.evaluateFinalAnswers`,
    `LlmResponseValidator.validateFinalEvaluation`을 4개 전체 `QuestionAnswerPair` 입력
    기준으로 다시 구현한다. `MockLlmClient`는 retained 2문항도 고정값이 아니라 실제
    `userAnswer`/`expectedAnswer`를 비교해 평가하도록 고친다(3문항 구조에서 남아있던
    "재평가 없이 고정 score" 동작 제거).
- 동시에 `QuestionEvaluation`(global.llm.dto)에 5개 루브릭 필드(`technicalAccuracy`,
  `coreCoverage`, `reasoning`, `specificity`, `tradeOffsAndExceptions`)를 추가한다.
  필드명은 `domain.judgment.llm.dto.RubricScores`와 동일하게 맞춘다.
- 김한비(LLM 호출·스키마 검증·재시도) vs 최용성(루브릭 clamp·재계산) 경계는 그대로
  유지한다 — `global.llm` 계층은 4문항 전체와 5개 루브릭 원시값을 그대로 전달하는
  역할만 하고, clamp·재계산·최종 합산 로직은 여전히 judgment(최용성) 담당으로 남긴다.
- **최용성님 쪽(`domain.judgment`)은 이 결정으로 인한 추가 수정 사항이 없다** — PR #18의
  `EvaluationLlmClient`/`RawQuestionEvaluation`/`RubricScores`는 이미 이 계약을 전제로
  완성되어 있으므로, 이번 정합화는 전적으로 `global.llm`(김한비) 쪽 수정이다.
- `LlmResponseValidator`의 스키마 검증에 5개 루브릭 필드 존재 여부를 포함시켜, 루브릭
  누락이 채점 호출 경계 안에서 최대 2회 재요청 대상이 되도록 한다.
- 착수는 PR #18 정리 완료 후로 한다(PR #18은 2026-07-13 머지 완료).

## 핵심 근거

judgment(PR #18)는 이미 4문항 전체·5개 루브릭 계약으로 구현이 끝났고, 이걸 되돌리는
것보다 `global.llm` 쪽을 그 계약에 맞추는 편이 변경 범위가 작다. 또한 채점 프롬프트가
이미 5개 항목을 계산해 반환하는데 `QuestionEvaluation`이 합산값만 옮기면 judgment가
원시값을 재구성할 방법이 없어, 두 도메인이 사실상 서로 다른 응답 스키마
(`RawQuestionEvaluation` vs `QuestionEvaluation`)를 각자 유지하게 된다. 필드와 문항
구성을 일치시키면 스키마 검증이 한 곳(`LlmResponseValidator`)에서 이뤄지고, 재시도
정책(NFR-05)도 원래 있던 호출 경계 안에서 계속 작동한다.

## 대안 및 반려

- **judgment(`domain.judgment`) 쪽을 3문항(retainedTurns) 구조에 맞춰 되돌리는 방식** —
  PR #18이 이미 4문항·5루브릭 계약으로 merge되어 있어 되돌리면 그 작업을 다시 해야
  하고, FR-04/API 명세(어제 정정 완료, 4문항·100점)와도 다시 어긋나게 되어 반려.
- **`QuestionEvaluation`은 그대로 두고 judgment가 별도로 LLM을 재호출해 루브릭을 다시
  받는 방식** — API 호출 횟수 가드(`llm-cost-policy.md`)를 위반하고 같은 답변을 두 번
  채점하게 되어 반려.
- **`global.llm` 계층에서 clamp까지 미리 처리해 judgment에 넘기는 방식** — 김한비/최용성
  경계(②는 LLM 호출까지, clamp는 ③ 책임)를 무너뜨려 반려.
- **`RawQuestionEvaluation`을 `domain.judgment`에서 `global.llm.dto`로 옮겨 하나로 통합**
  — 두 도메인이 서로의 패키지를 직접 참조하게 되어 도메인 중심 패키지 분리(ADR-001)를
  침해해 반려.

## 결과 (기대)

- `global.llm`의 최종 채점 요청·응답이 judgment가 요구하는 4문항 전체 구조와 정확히
  일치해, 두 계약 사이 변환/누락 없이 그대로 연결된다.
- `QuestionEvaluation`이 5개 루브릭을 그대로 담아 judgment에 전달되고, judgment는 이
  값을 clamp·재계산에만 사용한다.
- `MockLlmClient`가 retained 2문항도 실제 답변 기준으로 평가하게 되어, Mock 모드에서도
  4문항·100점 만점 계약을 정확히 재현한다.
- 루브릭·문항 누락은 `LlmResponseValidator`에서 걸러져 재시도(최대 2회) 대상이 된다.
- 최용성님 쪽 코드는 변경 없음 — 이번 정합화로 인한 회귀 리스크가 judgment 도메인에는
  없다.

## 관련 문서

- [FR-04](../requirements/functional-requirements.md)
- [IS-002/IS-002b](../api/api-spec.md)
- [ADR-008](ADR-008-evaluation-response-dto-split.md) — 초기/최종 채점 DTO 분리
- [ADR-009](ADR-009-judgment-evaluation-port.md) — judgment 채점 포트와 Mock 평가 경계
- 이슈 #24, PR #18
- [최용성 owner 문서](../ai/owners/choi-yongseong.md)
- [김한비 owner 문서](../ai/owners/kim-hanbi.md)
