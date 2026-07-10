# invariants-and-state-machines.md — 상태 전이 및 불변식

> 근거: `[기획서] 커리어 던전 v5.1.pdf` 3장#4(레벨 해금), 15장(ERD 설계), API 명세서
> `RS-002`/`IS-001`/`IS-002`. 이 문서는 `docs/ai/SHARED.md` §3 역방향 추적 ①(DB 제약)의
> 판단 기준 중 "상태값이 올바른 시점에 올바른 값으로 바뀌는가"를 다룹니다.

## 1. `Resume.parseStatus`

```
PROCESSING --(PDFBox 추출 성공)--> DONE
PROCESSING --(추출 실패)--------> FAILED
```

- 초기값은 항상 `PROCESSING` (업로드 직후, `RS-001` 응답).
- `DONE`이 된 이후에는 되돌아가지 않는다. 재업로드(동일 `type` UPSERT)는 새 레코드
  갱신이므로 상태를 다시 `PROCESSING`으로 초기화한다(FR-01).
- `FAILED`가 되면 사용자에게 재업로드를 안내한다(FR-01 예외처리). `FAILED` 상태에서
  자동 재시도는 하지 않는다(수동 재업로드만).
- 불변식: `type=RESUME`가 없는 상태(즉 `RESUME` 레코드가 없거나 `parseStatus != DONE`)로
  면접 세션(`IS-001`)을 생성하려는 시도는 반드시 차단된다(FR-01).
- 폴링: `RS-002 GET /api/resumes/{resumeId}`로 조회(NFR-04). 프론트는 `DONE`/`FAILED`가
  될 때까지 고정 주기로 재조회한다(`docs/adr/ADR-004-polling-over-sse.md`).

## 2. `InterviewSession.status`

기획서 15장에 정의된 상태값 위치와 전이:

```
IN_PROGRESS       -- 1차 답변 3개 제출(IS-002, 최저점 존재) --> AWAITING_FOLLOWUP
IN_PROGRESS       -- 1차 답변 3개 제출(동점 없음 등 판정 로직상 종료 조건) --> COMPLETED
AWAITING_FOLLOWUP -- 꼬리질문 답변 제출(IS-002b) --------------> COMPLETED
```

- `IN_PROGRESS`: 세션 생성(`IS-001`) 직후, 질문 3개가 발급된 상태. 1차 답변 대기.
- `AWAITING_FOLLOWUP`: 1차 배치채점 완료, 꼬리질문(`questionId=4`) 발송 후 그 답변 대기.
  FR-04에 따라 **꼬리질문은 무조건 발동**하므로, 실질적으로 `IN_PROGRESS` →
  `AWAITING_FOLLOWUP` → `COMPLETED` 경로만 존재한다(조건부로 곧바로 `COMPLETED`로 가는
  경로는 v5.1/v5.2 요구사항상 없다 — 구현 중 다른 경로가 필요하다고 판단되면 이 문서를
  먼저 갱신하고 팀에 공지할 것).
- `COMPLETED`: 최종 판정(`JudgmentResult`) 생성 완료. 이후 이 세션에 대한 추가 답변
  제출은 거부한다(`IS-002`/`IS-002b`는 상태에 따라 배치채점/최종판정으로 자동 분기하므로,
  `COMPLETED` 세션에 대한 재호출은 별도로 막아야 한다 — 역방향 추적 ② 예외 핸들러 대상).
- 불변식: `JudgmentResult`가 존재하는 세션은 반드시 `status=COMPLETED`여야 하고, 그
  역(=`COMPLETED`인데 `JudgmentResult` 없음)도 성립하지 않아야 한다
  (`JudgmentResult.sessionId` UNIQUE 제약, `docs/erd/entity-definition.md` 참고).

## 3. 레벨 해금 (`UserUnlockStatus`)

- 판정(`totalScore`, 0~100) 결과 **80점 이상**이면 다음 레벨을 해금하고
  `progressGauge`가 +30 누적된다(FR-05). 80점 미만이면 상태 변화 없음(Fail).
- 불변식: `unlockedLevel`이 N이면, 1..N 레벨의 페르소나는 모두 `unlocked=true`여야
  한다(레벨을 건너뛰고 해금되는 경우는 없음 — 순차 해금).
- 레벨 해금과 뱃지 지급(`UserBadge` 생성)은 같은 트랜잭션 안에서 일관되게 처리되어야
  한다 — 부분 실패로 게이지만 오르고 뱃지가 안 지급되는 상태는 불변식 위반이다
  (`docs/ai/owners/choi-yongseong.md` 체크리스트).
- ⚠️ **진행도 게이지 비율 미확정**: `docs/api/api-spec.md`의 `UM-001` 비고에 따르면
  "30, 30, 40을 채워서 100으로 맞출 예정"이라고 되어 있어, 마지막 레벨의 증가량이
  30이 아닌 40일 수 있다. 확정 전까지 `+30` 고정값을 하드코딩하지 않고 상수/설정으로
  분리한다.

## 4. `persona_config` (기획서 15장 원본 정의)

기획서 15장은 ERD 문서(엔티티정의서)보다 더 구체적인 필드 설명을 제공합니다. 두 문서가
어긋나면 이 절과 `docs/erd/entity-definition.md`를 함께 갱신하세요.

- `id`: PK
- `level`: 1(대리) / 2(과장) — **MVP는 2개로 고정**. Lv.3(압박 페르소나)은 스트레치골이며
  `comingSoon=true`로 UI에 잠금 표시만 한다(`IV-001` 비고).
- `tone`: `'널널함'` / `'깐깐함'` (API 레벨에서는 `lenient`/`strict`로 노출, `IV-001` 응답
  예시 참고)
- `subject_scope`(전문주제) 필드는 **없음** — 주제는 매 면접마다 사용자가 선택하는
  `InterviewSession.selectedKeyword`로 대체된다. 페르소나 자체는 주제에 종속되지 않는다.

## 5. 뱃지 지급 트리거 (FR-09)

```
가입 완료          --> Stage1 지급
Lv.1(널널한 대리) 해금 --> Stage2 지급
Lv.2(깐깐한 과장) 해금 --> Stage3 지급
Lv.3(압박 페르소나) 해금 --> Stage4 지급 (MVP 범위에서는 실제 지급 불가 — Lv.3 구현 시에만 활성화)
```

- ⚠️ **Stage 매핑 확인 필요**: `docs/api/api-spec.md` `BG-001` 비고에 "Stage3/4 매핑은
  원문(Lv.3/Lv.4 표기) 대비 한 칸 당겨 해석한 것"이라고 명시돼 있습니다. 이 문서와
  `docs/ai/owners/choi-yongseong.md`는 위 4단계 해석을 기준으로 채택했습니다. 구현 전
  팀 확인이 끝나면 이 절의 "⚠️" 표시를 제거하세요.

## 갱신 규칙

이 문서는 설계 문서이므로, 실제 코드에 상태값(enum)이나 전이 로직을 추가/변경할 때는
반드시 이 문서를 먼저 갱신하고 커밋에 함께 포함합니다(`docs/ai/SHARED.md` §3 ① DB 제약
역방향 추적).
