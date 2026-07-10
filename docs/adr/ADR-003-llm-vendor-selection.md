# ADR-003 — LLM 벤더 선정 (Claude 확정, Gemini/GPT 탈락)

- 상태: 승인 (기획서 v5.1, 16장). 세부 모델(Haiku 4.5 vs Sonnet 4.6)은 ⚠️ TBD —
  2주차 실연동 전 프롬프트 테스트 후 확정 (`docs/requirements/mvp-scope.md` FEAT-13).
- 근거 문서: 기획서 8장(기술 스택), 16장(ADR)

## 배경

면접 질문 생성·채점에 사용할 LLM 벤더를 정해야 한다. 이 프로젝트는 루브릭 기반
장문 평가(문항당 5개 세부항목, 25점 만점)와 페르소나 톤 유지, 그리고 안정적인
JSON 출력을 동시에 요구한다 (FR-03, FR-04, NFR-05, NFR-08).

## 결정

**Claude(Haiku 4.5 / Sonnet 4.6)**를 벤더로 확정한다. Gemini, GPT는 검토 후 탈락.

- 호출부는 `LlmClient` 인터페이스로 추상화하고, 실제 벤더는 구현체로만 존재한다
  (NFR-09, 벤더 교체 시 구현체만 교체). 이 ADR은 "어떤 벤더를 쓸지"에 대한 결정이고,
  "어떻게 추상화할지"는 NFR-09/`docs/ai/owners/kim-hanbi.md`가 다룬다.
- 세부 모델(Haiku 4.5 vs Sonnet 4.6) 선택은 비용/응답속도/평가 정확도 트레이드오프를
  2주차 실연동 전 프롬프트 테스트로 확정한다. 확정 전까지 모델명을 코드에 하드코딩하지
  않는다 (`docs/ai/owners/kim-hanbi.md` 체크리스트).
- 운영 측 제공 API 키를 공용으로 사용하며, 예산 상한(hard limit)을 1차 방어선으로
  건다 (NFR-11, `docs/operations/llm-cost-policy.md`).

## 핵심 근거

- 루브릭 기반 장문 평가와 페르소나 톤 유지에 강점이 있고, JSON 출력 안정성을 지원한다.
- 운영 측에서 API 키를 제공해 비용 부담이 없다 (기획서 8장).

## 대안 및 반려

- **Gemini** — 탈락 (기획서 16장, 세부 사유는 팀 브레인스토밍 메모 참고).
- **GPT** — 탈락 (동일).

## 결과 (기대)

- `LlmClient` 인터페이스 뒤에서 Mock 구현체(개발 전체 기간)와 실 구현체(통합테스트·
  데모 한정)를 교체하며 사용한다 (기획서 18장 테스트 전략의 "Mock 활용 전략").

## 관련 문서

- `docs/requirements/functional-requirements.md` — NFR-09(벤더 추상화)
- `docs/operations/llm-cost-policy.md` — 예산 상한, Mock 우선 정책
- `docs/ai/owners/kim-hanbi.md` — `LlmClient` 인터페이스 규칙
