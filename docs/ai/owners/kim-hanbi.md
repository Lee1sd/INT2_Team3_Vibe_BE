---
owner: kim-hanbi
domain: "② 면접 엔진 + LLM"
paths:
  - "src/main/java/com/careerdungeon/domain/interview/**"
  - "src/main/java/com/careerdungeon/domain/message/**"
  - "src/main/java/com/careerdungeon/domain/persona/**"
  - "src/main/resources/prompts/persona/**"
  - "src/main/resources/prompts/question-generation/**"
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
src/main/resources/prompts/persona/**
src/main/resources/prompts/question-generation/**
```

## 채점 프롬프트(`prompts/scoring/**`) 소속 변경 — 2026-07-27부로 ③(최용성) 이관

`resources/prompts/scoring/**`(`system.txt`, `initial-user.txt`, `final-user.txt`)는
과거 ②(김한비) 소속이었으나, 채점 루브릭·합격선 정책과 실제 수정 이력(최용성이 여러 차례
직접 수정) 양쪽 다 ③ 영역과 더 밀접해 **③(최용성) 소속으로 재조정됨**. 질문 생성 프롬프트
(`resources/prompts/question-generation/**`, `resources/prompts/persona/**`)는 그대로 ②
소속 유지.

**이관 범위**: 2026-07-27 시점 이후의 **모든 변경**이 ③ 소관입니다. "앞으로 새로 시작하는
작업만"이 아니라, 그 시점에 아직 끝나지 않은 작업(이미 열려 있던 이슈, 리뷰 중이던 변경
포함)도 ③으로 넘어갑니다 — 파일 하나에 소유자가 둘 생기는 상태를 두지 않기 위함입니다.
이미 머지된 과거 커밋의 저자를 소급해서 바꾸지는 않습니다.

## 손대지 말 것

- `resources/prompts/scoring/**` — 2026-07-27부로 ③(최용성) 소속. 채점 루브릭·구간 기준
  문구를 수정하려면 최용성과 먼저 협의할 것.

- `domain/judgment/**`, `domain/progress/**` — ②의 책임은 "LLM이 평가 원시값을 반환하는
  시점"까지입니다. 그 이후(루브릭 적용, 게이지 반영, 뱃지 판정)는 최용성(③)의 책임이며
  절대 여기서 처리하지 않습니다.
- `global/security/**` — 세션 소유자 검증(내 면접인지 확인)은 필요하지만, 인증 자체(토큰
  발급/검증)는 표지민(④) 소유입니다.

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/api-spec.md` | `IS-001`(세션 생성+질문 4개), `IS-002`/`IS-002b`(답변 제출·꼬리질문), `KW-001`, `IV-001` |
| `docs/requirements/functional-requirements.md` | FR-02(키워드), FR-03(질문 생성), FR-04(판정, ②는 LLM 호출까지), FR-08(종합 피드백), FR-12(이름 개인화), NFR-04~10, NFR-12 |
| `docs/requirements/mvp-scope.md` | FEAT-05, FEAT-08, FEAT-11, FEAT-13, FEAT-18, FEAT-20 |
| `docs/erd/entity-definition.md` | `PersonaConfig`, `InterviewSession`, `Message` 엔티티 |
| `docs/requirements/wbs.md` ② 섹션 | **작업 순서(v5.2)**: 인터페이스 추상화(1순위) → 응답 방어(2순위) → 페르소나/프롬프트/질문 생성 |
| `docs/operations/llm-cost-policy.md` | Mock 기본 원칙, 예산 상한, 호출 횟수 가드, 세부 모델 TBD |
| `docs/operations/failure-policy.md` §2 | LLM 응답 실패 처리 방침 참고 |
| `docs/state/invariants-and-state-machines.md` §2, §4 | `InterviewSession.status` 전이, `persona_config` 정의 |
