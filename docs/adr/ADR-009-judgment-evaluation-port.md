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
- Mock은 `evaluations`, `totalScore`, `weakestQuestionId`, `passed`, `overallFeedback`과 문항별
  5개 루브릭 점수를 반환한다.
- judgment 서비스는 LLM이 계산한 문항 점수·총점·최저점·합격 여부를 신뢰하지 않고,
  항목별 clamp 후 모두 재계산한다.
- 실제 Claude 연동은 이 ADR 승인과 김한비 owner 합의 후 별도 어댑터로 연결한다. 해당 어댑터는
  기존 `global.llm.LlmClient`/`LlmInvocationService`의 재시도·타임아웃 계약을 우회하거나
  judgment 도메인에서 벤더 SDK를 직접 참조해서는 안 된다.

## 핵심 근거

도메인 내부 Mock은 최용성 담당 경계를 지키면서도 FR-04의 20개 숫자 누락과 점수 범위 이탈을
외부 비용 없이 재현한다. 실제 호출 경로는 기존 공통 LLM 계약에 남겨 벤더 추상화와 재시도
정책을 유지하고, 교차-owner 배선은 승인 전까지 만들지 않는다.

## 대안 및 반려

- **기존 `global.llm.MockLlmClient` 직접 확장** — 실제 배선은 간단하지만 다른 owner 경로와
  질문 생성 계약까지 수정해야 하므로 이번 이슈 범위에서 반려했다.
- **judgment 도메인에서 Claude SDK 직접 호출** — owner 경계, NFR-09 벤더 추상화,
  비용·재시도 가드를 모두 우회하므로 반려했다.
- **고정 문항 점수만 반환** — 모범답변 비교, 5개 루브릭 누락, 항목별 clamp를 검증할 수 없어
  반려했다.

## 결과 (기대)

- 외부 LLM 없이 모범답변 변화, 무응답, 20개 루브릭 숫자, 필드 누락, 범위 초과를 테스트한다.
- Claude 연동 전까지 Mock이 기본 구현이며 실제 API 비용은 발생하지 않는다.
- interview에서 생성한 모범답변 전달과 재시도 배선은 교차-owner 후속 작업으로 명시된다.

## 관련 문서

- [FR-04](../requirements/functional-requirements.md)
- [IS-002/IS-002b](../api/api-spec.md)
- [LLM 비용 정책](../operations/llm-cost-policy.md)
- [LLM 실패 정책](../operations/failure-policy.md)
- [최용성 owner 문서](../ai/owners/choi-yongseong.md)
