# ADR-007 — LLM 세부 모델 확정 — Claude Haiku 4.5

- 상태: 승인
- 작성자: 김한비
- 작성일: 2026-07-13
- 관련 이슈/PR: 코드펜스 이슈 #8 (본 결정과는 무관 — §핵심 근거 참고)

## 배경

[ADR-003](ADR-003-llm-vendor-selection.md)에서 LLM 벤더는 Claude로 확정했지만, 세부
모델(Haiku 4.5 vs Sonnet 4.6)은 미정으로 남겨두었다. 당시 결정은 "비용/응답속도/평가
정확도 트레이드오프를 2주차 실연동 전 프롬프트 테스트로 확정한다"였고
(`docs/requirements/open-questions.md` #4), 확정 전까지는 모델명을 코드에 하드코딩하지
않기로 했다(`docs/ai/owners/kim-hanbi.md` 체크리스트). 이 ADR은 그 테스트 결과를 근거로
세부 모델을 최종 확정한다.

## 결정

**Claude Haiku 4.5**로 확정한다. `docs/operations/llm-cost-policy.md` §3의 모델 선택
설정값을 이 결정에 맞춘다.

## 핵심 근거

- Mock 답변으로 Haiku/Sonnet 각 1회 비교 테스트를 수행했다.
- 채점 방향(부실한 답변 → 낮은 점수)이 두 모델 모두 일치했고, feedback 품질도 동등했다.
- 점수 절대값 편차(4점 vs 2점)는 있었으나, 기획서 §12에서 이미 감수하기로 한 "완전한
  일관성 불가" 한계 범위 안이다 — 이 편차가 Sonnet을 선택할 근거가 되지 않는다.
- 비용을 고려하면 Haiku가 3주 예산(`docs/operations/llm-cost-policy.md` §2 예산 상한)에
  더 적합하다.
- 코드펜스 이슈(#8, 실 API 응답이 마크다운 코드펜스로 감싸져 오는 문제)는 모델 무관
  공통 방어 로직(파싱 전 코드펜스 벗기기)이라 이 모델 선택 결정에 영향을 주지 않는다.

## 대안 및 반려

- **Sonnet 4.6** — 반려. 채점 방향·feedback 품질이 Haiku와 동등해 품질 차이가 크지
  않은데 비용은 더 높아, 3주 예산 대비 우선순위가 낮다.

## 결과 (기대)

- `docs/operations/llm-cost-policy.md` §3의 모델명을 설정값으로 분리해 반영하고,
  하드코딩하지 않는다.
- `docs/requirements/open-questions.md` #4의 근거 링크가 이 ADR을 가리키도록 갱신한다.
- 실 LLM 클라이언트(Claude SDK) 구현 시 Haiku 4.5를 기본 모델로 사용한다
  (`docs/ai/owners/kim-hanbi.md` 체크리스트).

## 관련 문서

- [ADR-003](ADR-003-llm-vendor-selection.md) — LLM 벤더 선정(Claude 확정)
- `docs/operations/llm-cost-policy.md` §3 — 세부 모델 선택 설정
- `docs/requirements/open-questions.md` #4 — 세부 모델 확정 이력
- `docs/ai/owners/kim-hanbi.md` — 모델명 하드코딩 금지 체크리스트
