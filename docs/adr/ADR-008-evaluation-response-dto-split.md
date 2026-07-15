# ADR-008 — 채점 응답 DTO 분리 (InitialEvaluationResponse / FinalEvaluationResponse)

- 상태: 승인
- 작성자: 김한비
- 작성일: 2026-07-13
- 관련 이슈/PR: 이슈 #6, 이슈 #12
- 보완: 2026-07-13, FR-04 원기획에 따라 최종 응답을 4문항 합산으로 명확화
- 후속 결정: 2026-07-15, ADR-014가 최종 LLM 평가 범위를 turn 4 한 건으로 변경했다.
  외부 최종 결과가 1~4 점수를 포함하는 계약과 초기/최종 DTO 분리 결정은 유지된다.

## 배경

IS-002(최초 3문항 채점)와 IS-002b(꼬리질문 최종 채점)가 단일 `EvaluationResponse` DTO를
공유하고 있었다. 이슈 #6에서 지적된 바와 같이, IS-002b 응답은 API 계약상
`weakestQuestionId`가 없어야 하지만 공유 DTO에는 이 필드가 항상 존재해 실 LLM이 stale
`weakestQuestionId` 값을 최종 응답에 포함해도 검증을 통과하고 다운스트림(③ 판정·게이지
반영)으로 전달될 위험이 있었다. 이슈 #6 코멘트 스레드에서 이 문제를 두 방향으로
정리했다 — (1) `validateFinalEvaluation`에서 sentinel 값을 강제하는 방법, (2) 채점
단계별로 DTO 자체를 분리하는 방법. PR #4 스코프가 무거워지는 것을 피하기 위해 DTO 분리는
별도 이슈(#12)로 분리해 진행하기로 합의했다.

## 결정

`EvaluationResponse`를 다음 두 개의 record로 분리한다.

```java
// IS-002 최초 채점
record InitialEvaluationResponse(
    List<QuestionEvaluation> evaluations,
    int totalScore,
    int weakestQuestionId,
    boolean passed
) {}

// IS-002b 꼬리질문 포함 최종 채점
record FinalEvaluationResponse(
    List<QuestionEvaluation> evaluations, // questionId 1~4
    int totalScore,
    boolean passed,
    String overallFeedback
) {}
```

- `LlmClient` 인터페이스는 `evaluateAnswers` 단일 메서드 대신
  `evaluateInitialAnswers`/`evaluateFinalAnswers`로 분리한다.
- `LlmResponseValidator`의 `validateInitialEvaluation`/`validateFinalEvaluation`은 각각
  대응하는 타입만 받는다. 공통 구조 검증(null/empty/turn 범위/중복)은
  `List<QuestionEvaluation>`을 받는 private 헬퍼로 유지해 중복을 없앤다.
- `LlmInvocationService`도 `evaluateInitialAnswers`/`evaluateFinalAnswers`로 분리하고,
  각각 독립된 `@Retryable`/`@Recover` 쌍을 가진다.
- 최초 채점은 questionId `{1,2,3}`을 평가해 `weakestQuestionId`를 질문 생성 흐름에 전달한다.
  질문 생성 LLM이 questionId 4의 꼬리질문과 예상답변을 반환하면 호출 계층은 최초 3개
  질문·답변·예상답변과 합쳐 `{1,2,3,4}` 전체를 최종 채점 요청에 담는다.
- 최종 응답의 `evaluations`는 `{1,2,3,4}`와 정확히 일치해야 하며, 네 문항 합계로
  100점 만점과 80점 합격 여부를 계산한다. 1~3번 feedback은 생략 가능하고 4번은 필수다.
- `MockLlmClient`도 동일하게 분리하며, `FinalEvaluationResponse`에는
  `weakestQuestionId`를 아예 채우지 않는다(이전에는 `0`을 sentinel로 채워 넣었음).

## 핵심 근거

- **타입 수준 차단**: sentinel 강제 방식은 런타임 검증(`weakestQuestionId == 0` 확인)에
  의존하지만, DTO 분리는 애초에 필드 자체가 존재하지 않아 컴파일 타임에 오용이
  불가능하다. LLM 응답 방어(NFR-05, SHARED.md §3 역방향 추적 ⑤)를 가장 강하게 만족한다.
- **호출부와의 일관성**: 검증 메서드가 이미 `validateInitialEvaluation`/
  `validateFinalEvaluation`로 분리되어 있었다. DTO를 분리하면 호출 메서드
  (`LlmClient`, `LlmInvocationService`, `MockLlmClient`)도 자연스럽게 같은 경계로
  분리되어 코드 구조가 검증 로직과 일치한다.
- **③(최용성) 소비 지점 명확화**: 두 타입을 구분하면 판정 로직(③)이 "이 응답에
  weakestQuestionId가 있는지"를 매번 null/sentinel 체크할 필요 없이 타입만으로 구분할
  수 있다.
- **100점 총점 유지**: 최종 응답은 questionId 1~4의 평가를 모두 포함한다. 기존 retained
  2문항+꼬리질문 1문항 계약은 최대 75점이라 80점 합격 기준과 양립하지 않아 반려한다.

## 대안 및 반려

- **sentinel 값 강제** — 반려. `validateFinalEvaluation`에서 `weakestQuestionId == 0`을
  강제하거나 `LlmInvocationService`/`LlmClient` 진입부에서 값을 0으로 덮어쓰는 방식.
  구현이 더 작지만, 필드가 여전히 타입에 존재해 향후 코드 변경 시 다시 값이 새어나갈
  여지가 남는다 — 이슈 #6이 애초에 이 문제로 발생했다.

## 결과 (기대)

- `EvaluationResponse.java` 삭제, `InitialEvaluationResponse.java`/
  `FinalEvaluationResponse.java` 신설.
- `LlmClient`, `LlmInvocationService`, `MockLlmClient`, `LlmResponseValidator`의 채점
  관련 메서드가 IS-002/IS-002b 경계로 분리됨.
- 테스트 4개 파일(`LlmResponseValidatorTest`, `LlmInvocationServiceRetryTest`,
  `MockLlmClientTest`, `LlmMockModeIntegrationTest`) 갱신 완료.
- ③(judgment 도메인) 구현 시 `FinalEvaluationResponse`에 `weakestQuestionId`를
  참조하는 코드를 작성할 수 없다 — 타입에 없으므로 컴파일 에러로 즉시 드러난다.
- 최종 `evaluations`는 questionId `{1,2,3,4}`를 모두 포함해 100점 만점 판정을 보장한다.

## 관련 문서

- 이슈 #6: https://github.com/Lee1sd/INT2_Team3_Vibe_BE/issues/6
- 이슈 #12: https://github.com/Lee1sd/INT2_Team3_Vibe_BE/issues/12
- `docs/ai/owners/kim-hanbi.md` — 작업 순서 절, IS-002b 관련 체크리스트
- `src/main/java/com/careerdungeon/global/llm/validation/LlmResponseValidator.java`
