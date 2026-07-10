# open-questions.md — SSOT 불일치 · 팀 확인 필요 항목 모음

> 이 문서는 각 문서/owner 파일에 흩어져 있는 "⚠️ 팀 확인 필요" 항목을 한곳에서 볼 수
> 있게 모은 색인입니다. **각 항목의 상세 근거와 임시 채택 기준은 링크된 원본 위치에
> 있습니다** — 이 문서는 요약만 하고, 원본을 정본으로 취급합니다. 항목이 확정되면
> 여기서 지우지 말고 "확정됨"으로 상태만 바꾸고, 확정 근거를 한 줄 남기세요.

| # | 항목 | 불일치 내용 | 임시 채택 기준 | 원본 위치 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 1 | 이력서/포트폴리오 업로드 개수 | 마이페이지 목업 화면은 "최대 3개"처럼 보이는 UI, 요구사항명세서 FR-01은 "이력서 정확히 1개(필수) + 포트폴리오 최대 1개(선택)" | FR-01(원문 요구사항명세서)을 정본으로 채택 | `docs/ai/owners/lee-geonhui.md` | ⚠️ 미확정 |
| 2 | 뱃지 Stage 매핑 | API 명세서 `BG-001` 비고: "Stage3/4 매핑은 원문(Lv.3/Lv.4 표기) 대비 한 칸 당겨 해석한 것" | 가입직후=Stage1 / Lv.1해금=Stage2 / Lv.2해금=Stage3 / Lv.3해금=Stage4(FR-09 기준) | `docs/api/api-spec.md` (`BG-001`), `docs/state/invariants-and-state-machines.md` §5 | ⚠️ 미확정 |
| 3 | 진행도 게이지 비율 | FR-05는 "레벨 클리어마다 +30%씩 누적"이라고만 되어 있으나, `UM-001` 비고는 "30, 30, 40 채워서 100으로 맞출 예정"이라고 언급 — 마지막 레벨 증가량이 30이 아닐 수 있음 | 상수/설정으로 분리, `+30` 하드코딩 금지 | `docs/api/api-spec.md` (`UM-001`), `docs/state/invariants-and-state-machines.md` §3 | ⚠️ 미확정 |
| 4 | LLM 세부 모델 | 벤더는 Claude로 확정, Haiku 4.5 vs Sonnet 4.6은 미정 | 2주차 실연동 전 프롬프트 테스트 후 확정. 확정 전 모델명 하드코딩 금지 | `docs/adr/ADR-003-llm-vendor-selection.md`, `docs/operations/llm-cost-policy.md` §3 | ⚠️ 미확정 (2주차 확정 예정) |
| 5 | 레벨 해금 범위 서술 불일치 | 기획서 3장#4 본문은 "MVP 범위 내 **단일 레벨(대리)** 집중"이라고 서술하지만, 같은 기획서 4장 "MVP 페르소나" 표와 API 명세서 `IV-001`(Lv.1 unlocked / Lv.2 잠김, 게이지로 해금)은 **Lv.1+Lv.2 2개 레벨**을 MVP로 명시함. `mvp-scope.md`의 `FEAT-09`(레벨 해금 판정)도 2단계 해금 로직을 전제로 함 | **2개 레벨(Lv.1, Lv.2) 해금 구조를 정본으로 채택**한다 — API 명세서·FR-05·MVP 기능명세서 3곳이 일치하고, 3장#4의 "단일 레벨" 서술은 이전 초안의 잔존 문구로 판단 | `docs/api/api-spec.md`(`IV-001`), `docs/requirements/functional-requirements.md`(FR-05), `docs/requirements/mvp-scope.md`(`FEAT-09`) | ✅ 임시 확정(위 3문서 일치 근거) — 팀이 "단일 레벨"이 맞다고 확인하면 이 행을 갱신 |
| 6 | 공통 응답 포맷/예외 계약 | `docs/api/api-contract.md`는 아직 DRAFT | 표지민이 1주차 중 확정 (CM-002) | `docs/api/api-contract.md` | ⚠️ 미확정 |
| 7 | PII 마스킹 대상·방식 | `RS-001` 비고: "마스킹 대상·방식 팀 합의 필요" — 이메일 외 전화번호 등 추가 마스킹 대상 포함 여부 미확정 | 이메일 마스킹만 FR-11/NFR-13에 명시. 추가 대상은 팀 합의 후 이 문서와 FR-11을 갱신 | `docs/api/api-spec.md`(`RS-001`), `docs/requirements/functional-requirements.md`(FR-11) | ⚠️ 미확정 |

## 이 문서를 갱신하는 규칙

- 새로운 SSOT 불일치를 발견하면: (1) 원본 문서(owner 파일 또는 API/요구사항 문서)에
  먼저 ⚠️ 표시로 기록 → (2) 이 표에 한 행 추가 → (3) 그 주 금요일 회고
  (`docs/ai/workflows/generated/retro-week{N}.md`)에 발견 사실을 한 줄 남긴다.
- 팀이 확정하면: 상태 컬럼을 "✅ 확정(YYYY-MM-DD)"으로 바꾸고, 어떤 근거로 확정했는지
  한 줄 남긴다. 행을 삭제하지 않는다(나중에 왜 그렇게 정했는지 추적 가능해야 함).
