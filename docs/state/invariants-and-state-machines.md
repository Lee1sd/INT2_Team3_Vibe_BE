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
AWAITING_FOLLOWUP -- 꼬리질문 답변 제출(IS-002b) --------------> COMPLETED
```

- `IN_PROGRESS`: 세션 생성(`IS-001`) 직후, 질문 3개가 발급된 상태. 1차 답변 대기.
- `AWAITING_FOLLOWUP`: 1차 배치채점 완료, 꼬리질문(`questionId=4`) 발송 후 그 답변 대기.
  FR-04에 따라 **꼬리질문은 무조건 발동**하므로, 실질적으로 `IN_PROGRESS` →
  `AWAITING_FOLLOWUP` → `COMPLETED` 경로만 존재한다(조건부로 곧바로 `COMPLETED`로 가는
  경로는 v5.1/v5.2 요구사항상 없다 — 구현 중 다른 경로가 필요하다고 판단되면 이 문서를
  먼저 갱신하고 팀에 공지할 것).
- 최초 배치채점과 꼬리질문 메시지·예상답변 저장이 모두 성공한 직후 세션을
  `AWAITING_FOLLOWUP`으로 바꾼다. 저장과 상태 전이는 같은 트랜잭션으로 처리한다.
- 채점·꼬리질문 생성 LLM 호출은 DB 트랜잭션 밖에서 수행한다. 호출 전 준비 트랜잭션과
  호출 후 반영 트랜잭션에서 세션을 각각 잠그고 상태·소유자·중복 결과를 재검증한다.
  단일 인스턴스에서는 세션 단위 JVM 잠금으로 동일 세션 요청을 직렬화한다.
- `AWAITING_FOLLOWUP`에서 꼬리질문 답변을 받으면 turn 4만 LLM으로 채점한다. 서버는
  보존된 최초 1~3 점수와 turn 4 점수를 합쳐 100점 만점 총점을 만든다. 종합 피드백에는
  최초 1~3의 질문·답변·확정 점수·피드백을 읽기 전용 컨텍스트로 제공하되 재채점하지 않고,
  최종 판정 저장 성공 후에만 `COMPLETED`로 바꾼다.
- `COMPLETED`: 최종 판정(`JudgmentResult`) 생성 완료. 이후 이 세션에 대한 추가 답변
  제출은 거부한다(`IS-002`/`IS-002b`는 상태에 따라 배치채점/최종판정으로 자동 분기하므로,
  `COMPLETED` 세션에 대한 재호출은 별도로 막아야 한다 — 역방향 추적 ② 예외 핸들러 대상).
- 불변식: `JudgmentResult`가 존재하는 세션은 반드시 `status=COMPLETED`여야 하고, 그
  역(=`COMPLETED`인데 `JudgmentResult` 없음)도 성립하지 않아야 한다
  (`JudgmentResult.sessionId` UNIQUE 제약, `docs/erd/entity-definition.md` 참고).
- 최초 `AnswerScore` turn 1~3과 개별 피드백은 `AWAITING_FOLLOWUP` 전이 전에 모두
  저장되어야 한다. 최종 turn 4 답변·점수, `JudgmentResult`, 진행도·뱃지 변경,
  `COMPLETED` 전이는 하나의 트랜잭션으로 성공하거나 모두 롤백되어야 한다.
- `JudgmentResult.passed`는 `totalScore >= 80`과 항상 같아야 하며 애플리케이션과 DB
  CHECK 모두 이 불변식을 강제한다.

## 3. 레벨 해금 (`UserUnlockStatus`)

- 판정(`totalScore`, 0~100) 결과 **80점 이상**이면 다음 레벨을 해금하고
  `progressGauge`가 누적된다(FR-05). 80점 미만이면 상태 변화 없음(Fail).
  증가폭은 레벨마다 다르다: Lv.1 클리어 +30 / Lv.2 클리어 +30 / Lv.3 클리어 +40
  (합계 100). 균등 분배가 아니므로 레벨별 증가폭을 상수/설정으로 관리하고 `+30`을
  모든 레벨에 하드코딩하지 않는다.
- `progressGauge`는 **열린 Stage나 보유 뱃지 수가 아니라 클리어한 Stage의 누적 진행도**다.
  따라서 가입 직후에는 Stage1이 열리고 Stage1 뱃지를 보유해도 게이지는 0이며,
  Stage1/2/3을 클리어한 뒤 각각 30/60/100이 된다. 상한은 100이다.
- 불변식: `unlockedLevel`이 N이면, 1..N 레벨의 페르소나는 모두 `unlocked=true`여야
  한다(레벨을 건너뛰고 해금되는 경우는 없음 — 순차 해금).
- 레벨 해금과 뱃지 지급(`UserBadge` 생성)은 같은 트랜잭션 안에서 일관되게 처리되어야
  한다 — 부분 실패로 게이지만 오르고 뱃지가 안 지급되는 상태는 불변식 위반이다
  (`docs/ai/owners/choi-yongseong.md` 체크리스트).
- ✅ **진행도 게이지 비율 확정(2026-07-10)**: Lv.1 클리어 +30 / Lv.2 클리어 +30 /
  Lv.3 클리어 +40 = 100. `docs/requirements/open-questions.md` #3 참고.

## 4. `persona_config` (기획서 15장 원본 정의)

기획서 15장은 ERD 문서(엔티티정의서)보다 더 구체적인 필드 설명을 제공합니다. 두 문서가
어긋나면 이 절과 `docs/erd/entity-definition.md`를 함께 갱신하세요.

- `id`: PK
- `level`: 1(대리) / 2(과장) — **MVP 실구현은 2개까지**. Lv.3(압박 페르소나)·Lv.4는 모두
  스트레치골로, 면접 진행 로직(질문 생성·채점)은 아직 구현되지 않는다. Lv.3은
  `comingSoon=true`로 UI에 잠금 표시만 한다(`IV-001` 비고), Lv.4는 아직 `IV-001` 응답에도
  없다. 다만 뱃지 디자인(Level1~4 아트웍)은 Lv.4까지 이미 준비되어 있으므로, 향후 Lv.3/Lv.4를
  구현할 때 뱃지 자산을 새로 만들 필요는 없다.
- `tone`: `'널널함'` / `'깐깐함'` (API 레벨에서는 `lenient`/`strict`로 노출, `IV-001` 응답
  예시 참고)
- `subject_scope`(전문주제) 필드는 **없음** — 주제는 매 면접마다 사용자가 선택하는
  `InterviewSession.selectedKeyword`로 대체된다. 페르소나 자체는 주제에 종속되지 않는다.

## 5. 뱃지 지급 트리거 (FR-09)

```
가입 완료(unlockedLevel=1)         --> Stage1 지급
Lv.1 클리어(unlockedLevel: 1 -> 2) --> Stage2 지급
Lv.2 클리어(unlockedLevel: 2 -> 3) --> Stage3 지급
Lv.3 클리어(unlockedLevel: 3 -> 4) --> Stage4 지급 (스트레치골 — Lv.3 면접 로직 구현 시에만 활성화)
```

- ✅ **Stage 매핑 확정(2026-07-10)**: 트리거는 "레벨이 해금(unlocked)되어 있는 상태"가
  아니라 **"그 레벨을 클리어해서 다음 레벨로 넘어가는 순간"**을 기준으로 한다. 헷갈리기
  쉬운 부분이라 명확히 하면: Lv.1은 가입 시 기본으로 열려 있으므로 그 자체는 뱃지 트리거가
  아니고, "Lv.1을 클리어해서 Lv.2가 열리는 사건"이 Stage2를 준다.
- 게이지는 Stage 클리어 누적값이고 뱃지는 Stage 오픈 시 지급되므로 두 상태의 기준은 다르다.
  가입 시 Stage1 뱃지는 지급하지만 게이지는 0이며, 이후 클리어로 다음 Stage가 열릴 때
  해당 Stage 뱃지 지급을 별도 로직이 처리한다.
- 사용자별 동일 뱃지는 한 번만 지급한다. 애플리케이션의 멱등 확인과
  `user_badges(user_id, badge_id)` 복합 UNIQUE를 함께 적용하며, 게이지·해금·뱃지 생성은
  같은 트랜잭션에서 성공하거나 모두 롤백돼야 한다.
- Lv.3·Lv.4는 둘 다 스트레치골이다(MVP 3주 범위 밖). 면접 진행 로직은 아직 없지만, Stage1~4
  뱃지 디자인 자산(Level1.png~Level4.png)은 이미 다 만들어져 있으므로 그림 때문에 막히는
  일은 없다. `docs/requirements/open-questions.md` #2 참고.

## 갱신 규칙

이 문서는 설계 문서이므로, 실제 코드에 상태값(enum)이나 전이 로직을 추가/변경할 때는
반드시 이 문서를 먼저 갱신하고 커밋에 함께 포함합니다(`docs/ai/SHARED.md` §3 ① DB 제약
역방향 추적).
