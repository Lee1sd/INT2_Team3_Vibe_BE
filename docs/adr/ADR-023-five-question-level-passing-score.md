# ADR-023 — 5문항·문항당 20점 채점과 레벨별 합격선

- 상태: 제안
- 작성자: 최용성
- 작성일: 2026-07-24
- 관련 이슈/PR: #146, #147, PR #148, ADR-011, ADR-013, ADR-014

## 배경

질문 생성 계약이 최초 3문항에서 4문항으로 확장되면서, 꼬리질문을 포함한 최종 결과도
4문항에서 5문항으로 바뀌어야 한다. 기존 문항당 25점을 유지하면 총점이 125점이 되므로
외부 100점 계약을 유지할 수 없다. 또한 기존에는 모든 레벨에 80점 합격선을 적용했지만,
새 요구사항은 Lv.1 60점과 Lv.2 80점을 서로 다르게 적용한다.

## 결정

- 최초 채점 대상은 turn 1~4, 꼬리질문과 최종 LLM 채점 대상은 turn 5로 한다.
- 문항당 만점은 20점이며 루브릭을 기술적 정확성 8점, 핵심 내용 충족도 4점,
  근거·판단 과정 3점, 구체성·실무 연계 3점, 트레이드오프·예외 2점으로 배분한다.
- 최초 1~4의 서버 확정 점수는 보존한다. 최종 LLM은 turn 5만 채점하며 서버가
  `turn 1~4 확정 점수 + clamp(turn 5)`로 0~100 최종 점수를 계산한다.
- 합격선과 게이지 증가폭은 `StageGaugePolicy`를 단일 기준으로 관리한다.
  Lv.1은 60점·+30, Lv.2는 80점·+30이다. MVP 밖 Lv.3은 기존 80점·+40을 유지하되
  실제 Lv.3 면접 계약이 확정되면 다시 검토한다.
- `JudgmentResult.passed`는 세션의 페르소나 레벨과 위 합격선으로 서버가 계산한다.
  `judgment_results` 한 행만으로는 세션 레벨을 알 수 없어 교차 테이블 조건을 DB CHECK로
  표현하지 않는다. DB는 총점 0~100과 세션당 최종 판정 1건을 강제하고, 애플리케이션이
  평가 합격선·판정값·세션 레벨의 일치를 교차 검증한다.

## 핵심 근거

문항당 20점으로 재배분하면 다섯 문항을 단순 합산해 기존 100점 API를 유지할 수 있다.
최초 점수 보존 전략은 후속 LLM의 비결정성으로 기존 점수가 바뀌는 문제를 막는다.
레벨별 정책을 한 곳에 모으면 채점 판정과 진행도 해금의 합격선이 어긋나는 것을 방지한다.

## 대안 및 반려

- **최종 채점 호출에 세션 레벨을 별도 인자로 추가** — 합격선을 즉시 선택하기 쉽지만,
  김한비 담당 `InterviewService` 호출 계약을 변경해야 한다. 저장된 최초 평가에서 세션
  레벨 정책을 복원하면 owner 경계를 바꾸지 않고 동일한 불변식을 지킬 수 있어 반려했다.
- **모든 레벨에 80점 유지** — 코드 변경은 작지만 Lv.1 60점 요구사항을 충족하지 못해 반려했다.
- **`judgment_results`에 레벨 또는 합격선 컬럼 추가** — 단일 행 CHECK는 가능하지만 세션의
  페르소나 레벨과 중복 데이터를 저장하고 동기화해야 하므로 MVP 범위에서는 반려했다.
- **최초 4문항을 80점에서 100점으로 환산** — 최초 응답을 백분율로 볼 수 있지만 최종
  turn 5와 합산할 때 다시 역산해야 하며 문항별 20점 계약이 흐려져 반려했다.

## 결과 (기대)

- 외부 최종 평가에는 questionId 1~5가 포함되고, 각 점수는 0~20이며 총점은 0~100이다.
- Lv.1은 59/60, Lv.2는 79/80 경계에서 판정과 진행도 해금 결과가 달라진다.
- DB는 turn 1~5, 문항 점수 0~20, `isFollowUp=true`와 turn 5의 일치를 강제한다.
- 김한비 담당 질문 생성·답변 제출 계약은 PR #148의 4+1 turn 구조를 사용하며,
  이 ADR의 구현은 해당 PR을 선행 기반으로 한다.

## 관련 문서

- [`docs/api/api-spec.md`](../api/api-spec.md)
- [`docs/requirements/functional-requirements.md`](../requirements/functional-requirements.md)
- [`docs/requirements/open-questions.md`](../requirements/open-questions.md)
- [`docs/erd/entity-definition.md`](../erd/entity-definition.md)
- [`docs/state/invariants-and-state-machines.md`](../state/invariants-and-state-machines.md)
- [`docs/operations/flyway-migration-guide.md`](../operations/flyway-migration-guide.md)
