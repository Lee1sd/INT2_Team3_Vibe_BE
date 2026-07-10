# ADR-004 — 진행 상태 조회 방식: 클라이언트 폴링 (SSE 탈락)

- 상태: 승인 (기획서 v5.1, 16장)
- 근거 문서: 기획서 7장#4(백엔드 기술적 도전 과제), 16장(ADR)

## 배경

LLM 호출은 응답 지연이 3~10초 수준으로 발생한다. 프론트가 이 지연 동안 처리 상태
(`parseStatus`, 면접 세션 진행 상태)를 어떻게 알 수 있게 할지 정해야 한다.

## 결정

**클라이언트 폴링**으로 확정한다 (SSE 탈락). 이력서 파싱 상태(`RS-002 GET
/api/resumes/{resumeId}`)와 면접 처리 상태 모두 폴링 방식으로 조회한다.

- `parseStatus`: `PROCESSING` → `DONE`/`FAILED` (NFR-04)
- 타임아웃 발생 시 재시도 정책을 둔다 (NFR-04, `docs/operations/failure-policy.md`).
- ⚠️ 비동기 폴링 구조의 "서버 병목 개선"은 MVP 이후 과제로 `docs/requirements/wbs.md`
  ⑤ 인프라 섹션에 하 우선순위로 남아 있다.

## 핵심 근거

- 폴링은 구현이 단순하며, LLM 응답 지연(3~10초)을 커버하기에 충분하다.
- SSE는 연결 유지·재연결·프록시 호환성 등 3주 일정에서 감당하기 어려운 구현 복잡도를
  추가한다.

## 대안 및 반려

- **SSE(Server-Sent Events)** — 반려. 구현 복잡도가 폴링보다 높고, 3주 일정 내
  이점이 크지 않다고 판단.
- **WebSocket** — 검토되지 않음(기획서에 언급 없음). 폴링/SSE 트레이드오프보다 구현
  비용이 더 크므로 이 프로젝트 범위에서는 고려하지 않는다.

## 결과 (기대)

- 프론트는 고정 주기로 상태 조회 API를 호출하는 단순한 패턴만 구현하면 된다.
- 서버 측 폴링 부하는 MVP 기간에는 감당 가능한 수준으로 가정하고, 실측 후 필요하면
  운영 단계에서 개선한다 (`docs/requirements/wbs.md` ⑤ 인프라, 하 우선순위).

## 관련 문서

- `docs/requirements/functional-requirements.md` — NFR-04
- `docs/api/api-spec.md` — `RS-002`
- `docs/state/invariants-and-state-machines.md` — `parseStatus`, `InterviewSession.status` 상태값
