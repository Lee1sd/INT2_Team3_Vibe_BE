---
owner: choi-yongseong
domain: "③ 평가·게이지·해금"
paths:
  - "src/main/java/com/careerdungeon/domain/judgment/**"
  - "src/main/java/com/careerdungeon/domain/progress/**"
  - "src/main/resources/prompts/scoring/**"
team: CareerDungeon_Backend
---

# Owner — 최용성 (평가 · 게이지 · 해금)

> ⚠️ 이 파일은 "AI 면접관 페르소나"와 무관합니다. 이 파일은 **코드 담당자(최용성)의
> 작업 규칙**입니다. 페르소나 해금 조건 **콘텐츠**(Lv.1~4 신뢰도 기준 등)는 이 도메인이
> 다루지만, 파일 자체는 면접 콘텐츠가 아닙니다.

## 역할 한 줄

김한비(②)가 반환한 LLM 평가 원시값에 루브릭을 적용해 점수(`AnswerScore`)로 변환,
종합 판정(`JudgmentResult`) 생성, 신뢰도 게이지 계산, 레벨(Lv.1~4) 해금 조건 판정,
뱃지(`Badge`/`UserBadge`) 지급 트리거, 마이페이지 전적(History) 집계.

## 수정 가능 경로

```
src/main/java/com/careerdungeon/domain/judgment/**
src/main/java/com/careerdungeon/domain/progress/**
src/main/resources/prompts/scoring/**
```

## 채점 프롬프트(`prompts/scoring/**`) 소속 변경 — 2026-07-27부로 ③ 이관

`resources/prompts/scoring/**`(`system.txt`, `initial-user.txt`, `final-user.txt`)는
과거 ②(김한비) 소속이었으나, 채점 루브릭·합격선 정책과 실제 수정 이력 양쪽 다 ③ 영역과
더 밀접해 **③(최용성) 소속으로 재조정됨**. 질문 생성 프롬프트(`question-generation/**`,
`persona/**`)는 그대로 ② 소속.

## 손대지 말 것

- `domain/interview/**` — LLM 호출·재시도는 김한비(②) 책임입니다. ③은 ②가 넘겨준
  원시값을 "받는 시점"부터 시작합니다. LLM을 직접 호출하는 코드를 이 도메인에 새로
  추가하지 않습니다. (단, 채점 프롬프트 텍스트 자체는 위 소속 변경에 따라 ③ 책임입니다.)
- `domain/auth/**` — 가입 이벤트(뱃지 지급 트리거 중 하나)는 표지민(④)이 발행합니다.
  이벤트를 구독/판정하는 것은 ③이지만, 이벤트 발행 자체를 여기서 만들지 않습니다.

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/api-spec.md` | `IS-002`/`IS-002b`(채점 결과), `IV-001`(해금 정보), `BG-001`(뱃지), `HS-001`(히스토리), `UM-001`(진행도) |
| `docs/requirements/functional-requirements.md` | FR-04(③은 루브릭 적용부터), FR-05(레벨 해금·게이지), FR-09(뱃지), FR-10(히스토리) |
| `docs/erd/entity-definition.md` | `AnswerScore`, `JudgmentResult`, `Badge`, `UserBadge`, `UserUnlockStatus` 엔티티 |
| `docs/state/invariants-and-state-machines.md` §3, §5 | 레벨 해금·뱃지 지급 불변식, 게이지 비율 미확정 사항 |
| `docs/requirements/wbs.md` ③ 섹션 | "채점 AI"가 모범답변 리스트를 받아 반영하는 방식(FR-04 연계) |
| `docs/requirements/open-questions.md` | #2(뱃지 Stage 매핑), #3(게이지 비율), #5(레벨 해금 범위) |

## ✅ 확정된 항목 (2026-07-10, 과거 "팀 확인 필요" 항목)

상세 근거는 `docs/requirements/open-questions.md`가 SSOT이며, 여기서는 최용성 작업에
직접 영향을 주는 항목만 요약합니다. 아래 세 항목은 모두 확정되었으니 "팀 확인 필요"
문구가 남아있는 코드/주석이 있다면 제거하세요.

- **뱃지 Stage 매핑**: 가입직후(unlockedLevel=1)=Stage1, Lv.1 클리어(unlockedLevel→2)=Stage2,
  Lv.2 클리어(unlockedLevel→3)=Stage3, Lv.3 클리어(unlockedLevel→4, 스트레치골)=Stage4로
  확정. 트리거는 "레벨이 열려있는 상태"가 아니라 "그 레벨을 클리어해서 다음 레벨로
  넘어가는 시점"입니다. Lv.3·Lv.4 모두 스트레치골(면접 로직 미구현)이지만 뱃지 디자인
  자산(Level1~4.png)은 이미 준비돼 있으니 지급 로직 구조만 4단계로 설계해두세요.
- **진행도 게이지 비율**: Lv.1 클리어 +30% / Lv.2 클리어 +30% / Lv.3 클리어 +40% = 100%로
  확정(균등 분배 아님). 레벨별 증가폭을 상수/설정으로 분리하고 `+30`을 모든 레벨에
  하드코딩하지 마세요.
- **레벨 해금 범위**: 기획서 3장#4 본문의 "MVP 범위 내 단일 레벨(대리) 집중"은 이전
  초안의 잔존 문구로 확정. API 명세서 `IV-001`과 FR-05, `mvp-scope.md`의 `FEAT-09`가
  일치하는 **Lv.1+Lv.2 2단계만 MVP 실구현**하세요. Lv.3·Lv.4는 둘 다 스트레치골(면접
  로직 없음) — Lv.3은 `comingSoon=true`로 UI 표시만 하고, Lv.4는 아직 `IV-001`에도 없으니
  API 명세를 새로 추가하지 마세요(open-questions.md #5).
- **최종 채점 계약**: 5문항·문항당 20점·레벨별 합격선 정책은 `docs/adr/ADR-023-five-question-level-passing-score.md`가
  SSOT다. 구체적인 배점·합격선 수치는 여기 반복하지 않고 ADR-023과 `open-questions.md` #8을 참조한다.

## 체크리스트 (최용성)

- [ ] 점수 클램핑(문항당 0~20, 총점 0~100)을 서버에서 강제한다 — LLM/②가 범위 밖 값을
      넘겨도 그대로 저장하지 않는다 (NFR-05, 역방향 추적 ⑤ LLM 응답 방어의 연장).
- [ ] `JudgmentResult.sessionId`는 UNIQUE 제약이 실제 DDL에 있는가? (역방향 추적 ① DB 제약)
- [ ] 레벨별 커트라인(Lv.1 60점, Lv.2 80점) 이상 판정 시
      `UserUnlockStatus.unlockedLevel` 갱신 + `progressGauge` 누적이
      같은 트랜잭션 안에서 일관되게 처리되는가 — 부분 실패 시 게이지만 오르고 레벨이
      안 오르는 상황이 없는가? (FR-05)
- [ ] 동점 처리(최저점 답변이 여러 개일 때 랜덤 선택)가 실제로 구현되어 있는가? (FR-04)
- [ ] 레벨 해금과 뱃지 지급이 같은 트랜잭션 안에서 일관되게 처리되는가? (FR-09, 위 Stage 매핑 확인 항목 참고)
- [ ] 마이페이지 전적(History) 조회가 레벨별 그룹핑에서 N+1 쿼리 없이 동작하는가? (FR-10, HS-001)
- [ ] 종합 피드백(`overallFeedback`) 텍스트가 API 명세서 응답 예시의 필드명·형식과
      일치하는가 — `tierLabel`/`tierDescription` 필드는 정정되어 제거되었으니 다시
      추가하지 않는다 (역방향 추적 ③ 직렬화 설정, `IS-002b` 비고).
- [ ] 최근 N턴 윈도잉(멀티턴 컨텍스트 관리, NFR-06)이 채점 시점에 필요한 대화 맥락을
      빠뜨리지 않는가?
