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
| `docs/api/api-spec.md` | `IS-001`(세션 생성+질문 4개), `IS-002`/`IS-002b`(답변 제출·꼬리질문), `KW-001`, `IV-001` |
| `docs/requirements/functional-requirements.md` | FR-02(키워드), FR-03(질문 생성), FR-04(판정, ②는 LLM 호출까지), FR-08(종합 피드백), FR-12(이름 개인화), NFR-04~10, NFR-12 |
| `docs/requirements/mvp-scope.md` | FEAT-05, FEAT-08, FEAT-11, FEAT-13, FEAT-18, FEAT-20 |
| `docs/erd/entity-definition.md` | `PersonaConfig`, `InterviewSession`, `Message` 엔티티 |
| `docs/requirements/wbs.md` ② 섹션 | **작업 순서(v5.2)**: 인터페이스 추상화(1순위) → 응답 방어(2순위) → 페르소나/프롬프트/질문 생성 |
| `docs/operations/llm-cost-policy.md` | Mock 기본 원칙, 예산 상한, 호출 횟수 가드, 세부 모델 TBD |
| `docs/operations/failure-policy.md` §2 | LLM 응답 실패 처리 방침 참고 |
| `docs/state/invariants-and-state-machines.md` §2, §4 | `InterviewSession.status` 전이, `persona_config` 정의 |
