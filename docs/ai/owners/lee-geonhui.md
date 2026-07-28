---
owner: lee-geonhui
domain: "① 파일 파이프라인"
paths:
  - "src/main/java/com/careerdungeon/domain/resume/**"
team: CareerDungeon_Backend
---

# Owner — 이건희 (파일 파이프라인 · 이력서)

> ⚠️ 이 파일은 "AI 면접관 페르소나"와 무관합니다. 이 파일은 **코드 담당자(이건희)의
> 작업 규칙**입니다. 면접관 설정은 `domain.persona`, `docs/erd/entity-definition.md`의
> `PersonaConfig`를 참고하세요.

## 역할 한 줄

이력서(PDF/TXT/MD) 업로드, PDFBox·UTF-8 평문 기반 텍스트 추출, S3 저장/삭제,
데이터 풀(Resume/Portfolio) 관리, 활성 이력서(ACTIVE) 전환 로직.

## 수정 가능 경로

```
src/main/java/com/careerdungeon/domain/resume/**
```

## 손대지 말 것

- `domain/interview/**`, `domain/message/**` — LLM에 이력서 텍스트를 주입하는 시점부터는
  김한비(②)의 책임입니다. `resume` 도메인은 "추출된 텍스트를 어떤 형태로 제공하는가"까지만
  책임지고, 그 텍스트로 무엇을 하는지는 관여하지 않습니다.
- `global/security/**` — 업로드 API의 인증/인가 정책 자체를 바꿔야 한다면 표지민(④)에게
  먼저 알리세요. `resume` 컨트롤러에서 `@PreAuthorize` 등을 사용하는 것은 허용되지만,
  전역 보안 설정 변경은 표지민 소유입니다.

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/api-spec.md` | `RS-001`(업로드), `RS-002`(파싱 상태 폴링) |
| `docs/requirements/functional-requirements.md` | FR-01(업로드·추출), FR-11(PII 마스킹), NFR-01~02, NFR-03(이번 프로젝트 스코프 제외), NFR-14 |
| `docs/erd/entity-definition.md` | `Resume` 엔티티 (`type`, `s3Key`, `extractedText`, `parseStatus`, `fileHash`, `cacheExpiresAt`) |
| `docs/api/api-contract.md` | 에러 응답 포맷 확정 여부 (표지민 DRAFT) |
| `docs/requirements/privacy-policy.md` | 원본 삭제·추출 텍스트 파기·PII 마스킹 정책 (14장) |
| `docs/state/invariants-and-state-machines.md` §1 | `parseStatus` 상태 전이도 |

## ✅ 확정된 SSOT 불일치 (2026-07-10)

마이페이지 와이어프레임(`untitled/wireframes/06-mypage.svg`)에는 "이력서 데이터 풀
(Resume) 필수 (1/3)", "포트폴리오 데이터 풀 (Portfolio) (0/3)"처럼 **각각 최대 3개**까지
업로드하는 UI가 실제로 그려져 있습니다(SVG 텍스트 노드로 직접 확인). 이번에 팀이
**이 와이어프레임 기준을 정본으로 채택**했습니다:

- `RESUME`: 필수, **최소 1개 ~ 최대 3개**
- `PORTFOLIO`: 선택, **0개 ~ 최대 3개**

`docs/requirements/functional-requirements.md`(FR-01), `docs/erd/entity-definition.md`,
`docs/api/api-spec.md`(`RS-001`)는 이미 이 기준으로 갱신되었습니다. 구현 시 참고:
동일 `type`이 이미 3개인 상태에서 추가 업로드를 시도하면 400으로 거부하거나(가장 단순)
UI에서 기존 파일 교체를 유도하는 방식 중 선택해 구현하고, 어느 쪽으로 구현했는지
PR에 명시하세요. (색인: `docs/requirements/open-questions.md` #1)

진행 상태와 구현 체크리스트는 [`docs/ai/progress-lee-geonhui.md`](../progress-lee-geonhui.md)에서
관리합니다.
