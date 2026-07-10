---
owner: kim-hanbi
domain: "② 면접 엔진 + LLM"
paths:
  - "src/main/java/com/careerdungeon/domain/interview/**"
  - "src/main/java/com/careerdungeon/domain/message/**"
  - "src/main/java/com/careerdungeon/domain/persona/**"
  - "src/main/resources/prompts/**"
team: CareerDungeon_Backend
---

# Owner — 김한비 (면접 엔진 · LLM 통합)

> ⚠️ 이 파일은 "AI 면접관 페르소나(널널한 대리 / 깐깐한 과장)"와 무관합니다.
> 면접관 페르소나 **콘텐츠**(톤, 말투, 프롬프트 문구)는 이 파일이 다루는 대상이지만,
> 이 파일 자체는 **코드 담당자(김한비)의 작업 규칙**입니다. 두 개념을 혼동하지 마세요.

## 역할 한 줄

LLM 클라이언트 인터페이스(`LlmClient`)와 Mock 구현체, 면접관 스타일 엔진(톤·피드백 성향),
프롬프트 템플릿(이력서 키워드 주입, few-shot), 질문 생성·답변 처리 흐름, JSON 스키마 검증·
재시도, 면접 세션(`InterviewSession`)과 메시지(`Message`) 생성/조회.

## 수정 가능 경로

```
src/main/java/com/careerdungeon/domain/interview/**
src/main/java/com/careerdungeon/domain/message/**
src/main/java/com/careerdungeon/domain/persona/**
src/main/resources/prompts/**
```

## 손대지 말 것

- `domain/judgment/**`, `domain/progress/**` — ②의 책임은 "LLM이 평가 원시값을 반환하는
  시점"까지입니다. 그 이후(루브릭 적용, 게이지 반영, 뱃지 판정)는 최용성(③)의 책임이며
  절대 여기서 처리하지 않습니다.
- `global/security/**` — 세션 소유자 검증(내 면접인지 확인)은 필요하지만, 인증 자체(토큰
  발급/검증)는 표지민(④) 소유입니다.

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/api-spec.md` | `IS-001`(세션 생성+질문 3개), `IS-002`/`IS-002b`(답변 제출·꼬리질문), `KW-001`, `IV-001` |
| `docs/requirements/functional-requirements.md` | FR-02(키워드), FR-03(질문 생성), FR-04(판정, ②는 LLM 호출까지), FR-08(종합 피드백), FR-12(이름 개인화), NFR-04~10, NFR-12 |
| `docs/requirements/mvp-scope.md` | FEAT-05, FEAT-08, FEAT-11, FEAT-13, FEAT-18, FEAT-20 |
| `docs/erd/entity-definition.md` | `PersonaConfig`, `InterviewSession`, `Message` 엔티티 |
| `docs/requirements/wbs.md` ② 섹션 | **작업 순서(v5.2)**: 인터페이스 추상화(1순위) → 응답 방어(2순위) → 페르소나/프롬프트/질문 생성 |
| `docs/operations/llm-cost-policy.md` | Mock 기본 원칙, 예산 상한, 호출 횟수 가드, 세부 모델 TBD |
| `docs/operations/failure-policy.md` §2 | LLM 응답 실패 처리(재요청 2회, 폴백 지시 등) |
| `docs/state/invariants-and-state-machines.md` §2, §4 | `InterviewSession.status` 전이, `persona_config` 정의 |

## 작업 순서 (WBS v5.2 신규 명시)

가장 먼저 `LlmClient` 인터페이스 + Mock 구현체를 만드세요. Mock만 있어도 최용성(③)이
채점 루틴(JSON→점수 변환) 개발을 바로 시작할 수 있습니다. 그다음 JSON 스키마 검증
(`evaluations`, `totalScore`, `weakestQuestionId`, `passed` 등 필드명/타입)을 확정하세요 —
최용성이 이 형식에 맞춰 점수 변환 로직을 짭니다. 페르소나 스타일 엔진·프롬프트 템플릿·
질문 생성 조립 로직은 그 다음(중요도 상→중으로 조정됨, 안 중요하다는 뜻이 아니라 순서상
후순위라는 뜻).

## 체크리스트 (김한비)

- [ ] LLM 호출은 반드시 `LlmClient` 인터페이스를 통해서만 — 구현체(Claude SDK 등)를
      도메인 코드에서 직접 참조하지 않는다 (NFR-09 벤더 추상화). **FEAT-13이 아직 ⚠️
      TBD(Haiku 4.5 vs Sonnet 4.6)이므로 모델명을 하드코딩하지 않는다.**
- [ ] JSON 스키마 검증 실패 시 최대 2회 재요청 — 3회째는 `FAILED` 처리 후 사용자에게
      명확한 안내를 반환한다 (NFR-05, 역방향 추적 ⑤ LLM 응답 방어). FR-04는 한 번에
      20개 세부 숫자(5항목×4문항)를 채점해야 해서 이 리스크가 특히 크다.
- [ ] 문항당 5개 세부항목(기술적정확성10/핵심내용충족도5/근거판단과정4/구체성실무연계3/
      트레이드오프예외3=25점)의 세부 점수는 API·화면에 노출하지 않는다 — `score`+`feedback`만
      반환한다 (FR-04).
- [ ] 점수·게이지 관련 필드가 응답에 섞여 있다면, ②에서 clamp하지 않고 원시값 그대로
      ③에 넘긴다 — clamp는 ③의 책임이다 (경계 혼동 방지).
- [ ] 질문 생성 시 참고 질문 예시(few-shot)를 프롬프트에 포함하는가? (FR-03)
- [ ] 키워드-이력서 불일치(이력서에 없는 기술 스택 질문 등) 시 "일반 CS 지식 관점에서
      질문하라"는 폴백 프롬프트 문구를 유지하는가? (NFR-12)
- [ ] 질문 난이도 가이드라인이 프롬프트에 명시되어 있는가? (NFR-10)
- [ ] 사용자 이름이 질문/피드백 프롬프트에 동적으로 반영되는가(예: "OO님, ...")? (FR-12)
- [ ] Mock 모드가 기본값이다. 실 API 호출은 통합테스트/데모 프로필에만 한정한다
      (NFR-11, 역방향 추적 ⑥ 비용/시간 가드, FEAT-11).
- [ ] 키워드 목록 6종(데이터전처리/DB/부하/보안/시스템설계/클라우드) 중 MVP에서는
      2~3개만 노출하는가? (FR-02, FEAT-18)
