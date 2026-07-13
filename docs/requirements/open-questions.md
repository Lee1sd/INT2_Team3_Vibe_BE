# open-questions.md — SSOT 불일치 · 팀 확인 필요 항목 모음

> 이 문서는 각 문서/owner 파일에 흩어져 있는 "⚠️ 팀 확인 필요" 항목을 한곳에서 볼 수
> 있게 모은 색인입니다. **각 항목의 상세 근거와 임시 채택 기준은 링크된 원본 위치에
> 있습니다** — 이 문서는 요약만 하고, 원본을 정본으로 취급합니다. 항목이 확정되면
> 여기서 지우지 말고 "확정됨"으로 상태만 바꾸고, 확정 근거를 한 줄 남기세요.

| # | 항목 | 불일치 내용 | 채택 기준 | 원본 위치 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 1 | 이력서/포트폴리오 업로드 개수 | 실제 마이페이지 와이어프레임(`untitled/wireframes/06-mypage.svg`, SVG 텍스트 "이력서 데이터 풀 (Resume) 필수 (1/3)" / "포트폴리오 데이터 풀 (Portfolio) (0/3)")은 각각 최대 3개 업로드 UI. 요구사항명세서 CSV 원본(`WBS_Vibe v5.2 - 요구사항명세서.csv`) 및 이를 옮긴 `functional-requirements.md`(FR-01)·`api-spec.md`(RS-001)·`entity-definition.md`는 "이력서 정확히 1개(필수) + 포트폴리오 정확히 1개(선택)"로 서술 | **와이어프레임 기준(RESUME 최소 1~최대 3개, PORTFOLIO 0~최대 3개)을 정본으로 채택** | `docs/requirements/functional-requirements.md`(FR-01), `docs/erd/entity-definition.md`, `docs/api/api-spec.md`(`RS-001`), `docs/ai/owners/lee-geonhui.md` | ✅ 확정(2026-07-10) |
| 2 | 뱃지 Stage 매핑 | 기획서 5장의 "1~3단계 지급 시점" 서술(가입/Lv.1/Lv.2까지만 나열)과, 뱃지 디자인 설명 박스(Stage1~4가 Lv.1~4에 그대로 대응하는 것처럼 보이는 표기) 사이에 트리거 시점이 헷갈리게 서술됨 | 트리거는 "레벨이 열려있는 상태"가 아니라 "그 레벨을 클리어해서 다음 레벨로 넘어가는 시점" 기준: 가입(unlockedLevel=1)=Stage1 / Lv.1 클리어(→2)=Stage2 / Lv.2 클리어(→3)=Stage3 / Lv.3 클리어(→4, 스트레치골)=Stage4. Lv.3·Lv.4 모두 스트레치골(면접 로직 미구현)이나 뱃지 디자인(Level1~4.png)은 이미 4단계 전부 준비됨 | `docs/api/api-spec.md` (`BG-001`), `docs/state/invariants-and-state-machines.md` §5 | ✅ 확정(2026-07-10) |
| 3 | 진행도 게이지 비율 | FR-05는 "레벨 클리어마다 +30%씩 누적"이라고만 되어 있으나, `UM-001` 비고는 "30, 30, 40 채워서 100으로 맞출 예정"이라고 언급 — 마지막 레벨 증가량이 30이 아닐 수 있음 | Lv.1 클리어 +30% / Lv.2 클리어 +30% / Lv.3 클리어 +40% = 100%. 상수/설정으로 분리, `+30` 하드코딩 금지 | `docs/api/api-spec.md` (`UM-001`), `docs/state/invariants-and-state-machines.md` §3 | ✅ 확정(2026-07-10) |
| 4 | LLM 세부 모델 | 벤더는 Claude로 확정, Haiku 4.5 vs Sonnet 4.6은 미정 | 김한비가 2주차 실연동 전 프롬프트 테스트 후 확정. 확정 전 모델명 하드코딩 금지, 인터페이스 뒤로 추상화 | `docs/adr/ADR-003-llm-vendor-selection.md`, `docs/operations/llm-cost-policy.md` §3 | ✅ 확정(2026-07-13), 근거는 ADR-007 참고 |
| 5 | 레벨 해금 범위 서술 불일치 | 기획서 3장#4 본문은 "MVP 범위 내 **단일 레벨(대리)** 집중"이라고 서술하지만, 같은 기획서 4장 "MVP 페르소나" 표와 API 명세서 `IV-001`(Lv.1 unlocked / Lv.2 잠김, 게이지로 해금)은 **Lv.1+Lv.2 2개 레벨**을 MVP로 명시함. `mvp-scope.md`의 `FEAT-09`(레벨 해금 판정)도 2단계 해금 로직을 전제로 함 | **Lv.1+Lv.2 2개 레벨만 MVP 실구현으로 채택**한다 — API 명세서·FR-05·MVP 기능명세서 3곳이 일치하고, 3장#4의 "단일 레벨" 서술은 이전 초안의 잔존 문구로 판단. Lv.3·Lv.4는 둘 다 스트레치골(면접 로직 없음, 뱃지 디자인만 선준비) — Lv.3은 `comingSoon=true`로 UI 표시, Lv.4는 아직 `IV-001`에도 없음 | `docs/api/api-spec.md`(`IV-001`), `docs/requirements/functional-requirements.md`(FR-05), `docs/requirements/mvp-scope.md`(`FEAT-09`) | ✅ 확정(2026-07-10, 위 3문서 일치 + 표지민 재확인) |
| 6 | 공통 응답 포맷/예외 계약 | `docs/api/api-contract.md`가 DRAFT였음 — (a) 공통 래퍼 사용 여부, (b) 에러 응답 필드명, (c) `fieldErrors[]` 배열 추가 여부 3가지가 미정이었음 | (a) 래퍼 없음(데이터 그대로 반환) / (b) `{code, message, status}` 그대로 확정 / (c) `fieldErrors[]` 추가 안 함 — 표지민이 `global/exception/`에 `BusinessException`·`ErrorResponse`·`GlobalExceptionHandler` 구현 | `docs/api/api-contract.md` | ✅ 확정(2026-07-10, 담당: 표지민) |
| 7 | PII 마스킹 대상·방식 | 문서 간 실제 불일치는 아님 — 기획서·API명세서 모두 "이메일만 마스킹"으로 일관되게 서술됨. `RS-001` 비고의 "팀 합의 필요" 문구는 이 결정을 정식으로 확정(sign-off)하기 위해 이 표에 올려둔 것 | 이메일 마스킹만 최종 확정. FR-11/NFR-13에 명시된 대로 이메일 외 항목(전화번호 등)은 마스킹 대상에 포함하지 않음 | `docs/api/api-spec.md`(`RS-001`), `docs/requirements/functional-requirements.md`(FR-11) | ✅ 확정(2026-07-10) |
| 8 | 모범답변(expectedAnswer) 생성 시점 · questions 테이블 부재 | FR-03 처리 로직 4는 "질문 생성 호출 시 모범답변을 함께 생성"한다고 서술하는데, FR-04 처리 로직 1은 "채점 호출 내에서 모범답변을 생성하고 저장 안 함(휘발성)"이라고 서술 — 모범답변 생성 시점·저장 여부가 두 문서에서 서로 다르게 기술됨. ERD에는 질문·모범답변을 저장할 테이블이 없어 FR-03대로 가려면 신규 테이블이 필요함 | FR-03이 맞다 — 모범답변은 질문 생성 호출(FR-03)에서 함께 생성해 저장하고, 채점 호출(FR-04)은 저장된 값을 재사용한다(재생성 아님). FR-04의 "채점 호출 내 생성" 문구를 "질문 생성 호출 내 생성"으로 정정하고 ERD에 questions 테이블을 신규 반영한다. expectedAnswer는 채점 로직 내부에서만 쓰이고 API·화면에는 절대 노출하지 않는다(FR-03 처리 로직 5, FR-04 처리 로직 4와 일치) | `docs/requirements/functional-requirements.md`(FR-03, FR-04), `docs/erd/entity-definition.md` | ✅ 확정(2026-07-13, 김한비) — FR-03/ERD/FR-04 문서 정정 진행 중 |

## 이 문서를 갱신하는 규칙

- 새로운 SSOT 불일치를 발견하면: (1) 원본 문서(owner 파일 또는 API/요구사항 문서)에
  먼저 ⚠️ 표시로 기록 → (2) 이 표에 한 행 추가 → (3) 그 주 금요일 회고
  (`docs/ai/workflows/generated/retro-week{N}.md`)에 발견 사실을 한 줄 남긴다.
- 팀이 확정하면: 상태 컬럼을 "✅ 확정(YYYY-MM-DD)"으로 바꾸고, 어떤 근거로 확정했는지
  한 줄 남긴다. 행을 삭제하지 않는다(나중에 왜 그렇게 정했는지 추적 가능해야 함).
