# ADR-009 — judgment 채점 포트와 Mock 평가 구현의 경계

- 상태: 제안
- 작성자: 최용성
- 작성일: 2026-07-13
- 관련 이슈/PR: #5

## 배경

이슈 #5는 실제 Claude 채점 연동 전, 모범답변과 사용자 답변을 비교해 5개 루브릭 원시값을
만드는 Mock이 필요하다. 기존 `global.llm.MockLlmClient`를 확장하면 현재 호출 흐름에는 바로
연결되지만 최용성의 담당 경로를 벗어나며, 질문 생성과 채점 계약을 함께 변경하게 된다.
반대로 judgment 도메인은 LLM 원시값의 스키마 이탈과 범위 초과를 독립적으로 재현할 수 있어야 한다.

## 결정

- `domain.judgment`에 채점 전용 소비 포트와 원시 응답 모델을 둔다.
- 현재 구현체는 외부 호출이 없는 결정적 Mock으로 제한한다.
- ADR-008을 따라 포트와 원시 응답을 최초/최종 타입으로 분리한다.
  - 최초: questionId `{1,2,3}`, `evaluations`, `totalScore`, `weakestQuestionId`, `passed`
  - 최종: questionId `{1,2,3,4}`, `evaluations`, `totalScore`, `passed`, `overallFeedback`
- 질문 생성 LLM이 최저점 질문의 꼬리질문·예상답변을 반환하면 호출 계층이 최초 3개
  질문·답변·예상답변과 합쳐 4개를 최종 평가 포트에 전달한다.
- judgment 서비스는 LLM이 계산한 문항 점수·총점·최저점·합격 여부를 신뢰하지 않고,
  항목별 clamp 후 모두 재계산한다.
- 문항별 `score`는 선택 호환 필드로 유지하되 누락되어도 5개 루브릭으로 다시 계산한다.
- 중복·범위 밖 questionId나 질문·예상답변 누락은 재시도로 복구되지 않는 내부 입력 오류로
  처리하고, LLM 응답 필드·루브릭 누락만 `LlmSchemaValidationException`으로 처리한다.
- 실제 Claude 연동은 이 ADR 승인과 김한비 owner 합의 후 별도 어댑터로 연결한다. 해당 어댑터는
  기존 `global.llm.LlmClient`/`LlmInvocationService`의 재시도·타임아웃 계약을 우회하거나
  judgment 도메인에서 벤더 SDK를 직접 참조해서는 안 된다.

## 핵심 근거

도메인 내부 Mock은 최용성 담당 경계를 지키면서도 최초 15개·최종 20개 루브릭 숫자 누락과
점수 범위 이탈을 외부 비용 없이 재현한다. 단계별 타입 분리로 최종 응답에 오래된
`weakestQuestionId`가 섞이지 않게 하며, 실제 호출 경로는 기존 공통 LLM 계약에 남겨 벤더
추상화와 재시도 정책을 유지한다.

## 대안 및 반려

- **기존 `global.llm.MockLlmClient` 직접 확장** — 실제 배선은 간단하지만 다른 owner 경로와
  질문 생성 계약까지 수정해야 하므로 이번 이슈 범위에서 반려했다.
- **judgment 도메인에서 Claude SDK 직접 호출** — owner 경계, NFR-09 벤더 추상화,
  비용·재시도 가드를 모두 우회하므로 반려했다.
- **고정 문항 점수만 반환** — 모범답변 비교, 5개 루브릭 누락, 항목별 clamp를 검증할 수 없어
  반려했다.
- **꼬리질문 1개만 채점하고 서버에서 기존 점수 합산** — 호출 입력은 작지만 최종 LLM이
  네 문항 전체 문맥을 바탕으로 종합 피드백을 만들기 어려워 반려했다.

## 결과 (기대)

- 외부 LLM 없이 모범답변 변화, 무응답, 최초 3문항·최종 4문항 구성, 20개 루브릭 숫자,
  필드 누락, 범위 초과를 테스트한다.
- Claude 연동 전까지 Mock이 기본 구현이며 실제 API 비용은 발생하지 않는다.
- interview에서 생성한 모범답변 전달과 재시도 배선은 교차-owner 후속 작업으로 명시된다.

## 관련 문서

- [FR-04](../requirements/functional-requirements.md)
- [IS-002/IS-002b](../api/api-spec.md)
- [LLM 비용 정책](../operations/llm-cost-policy.md)
- [LLM 실패 정책](../operations/failure-policy.md)
- [최용성 owner 문서](../ai/owners/choi-yongseong.md)
