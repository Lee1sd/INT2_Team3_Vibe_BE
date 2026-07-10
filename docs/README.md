# docs — Career Dungeon 문서 인덱스 (SSOT)

이 폴더가 프로젝트의 단일 진실 공급원(SSOT)입니다. 사람과 AI 에이전트 모두 코드를 보기 전에
필요한 절만 여기서 찾아 읽습니다. 전체를 한 번에 다 읽지 않습니다 — 어떤 문서를 언제 읽을지는
`docs/ai/README.md`의 "경로 → 오너" 표와 각 `docs/ai/owners/*.md`의 "추가 필수 참조" 표를
따르세요.

> **처음이라면**: [`docs/ai/harness-engineering-101.md`](ai/harness-engineering-101.md)를
> 가장 먼저 읽으세요. "하네스 엔지니어링이 뭔지", "어떻게 쓰는지", "규칙을 어떻게 고치는지"를
> 쉬운 말로 설명합니다.

## 폴더 구성

| 폴더 | 내용 | 상태 |
| --- | --- | --- |
| [`ai/`](ai/README.md) | AI 에이전트 하네스 (harness-engineering-101, SHARED.md, owners, workflows, 코드 리뷰 문화) | ✅ 작성 완료 |
| [`adr/`](adr/README.md) | 아키텍처/하네스 의사결정 근거 (ADR 6건) + ADR 작성 가이드/템플릿 | ✅ 작성 완료 (세부 모델 선정 등 하위 TBD는 본문에 표시) |
| [`requirements/`](requirements/README.md) | 요구사항명세서(FR/NFR), MVP, 작업순서도, WBS + 기획서 기반 배경/마일스톤/테스트전략/보안/개인정보/미확정 항목 | ✅ 작성 완료 |
| [`api/`](api/README.md) | API 명세서 | ✅ 원본 CSV 옮김 / 공통 응답·에러 계약은 🟡 DRAFT(표지민 확정 대기) |
| [`erd/`](erd/README.md) | 엔티티정의서, ERD 이미지 | ✅ 원본 CSV + 이미지 옮김 |
| [`state/`](state/README.md) | 상태 전이(`InterviewSession.status`/`parseStatus` 등), 불변식 | ✅ 작성 완료 |
| [`operations/`](operations/README.md) | LLM 비용 정책, 실패 처리 정책 | ✅ 작성 완료 |
| `contributing/` | Git 협업 컨벤션 | ⬜ 별도 폴더 미작성 — `docs/ai/SHARED.md` §4가 SSOT(브랜치/커밋/PR/이슈 4대 규칙 확정됨) |

## 옮긴 원본

`requirements/`, `api/`, `erd/`의 CSV 파생 문서는 `C:\Users\dkwlr\Documents\프로그래머스_인턴쉽\`의
`WBS_Vibe v5.2 - *.csv` 6개와 ERD 이미지를 그대로 마크다운/이미지로 옮긴 것입니다.
`requirements/planning-overview.md`, `milestones.md`, `test-strategy.md`,
`security-design.md`, `privacy-policy.md`와 `adr/ADR-001·003~006`은 같은 폴더의
`[기획서] 커리어 던전_ AI 면접관 성장 시뮬레이터 v5.1.pdf`(기획서는 v5.1이 최신·최종
확정본)를 옮긴 것입니다. 원본이 갱신되면 이 문서들도 같이 갱신하고, 어긋난 부분을
발견하면 `docs/ai/workflows/generated/retro-week{N}.md`에 한 줄 남기세요.

CSV와 기획서 사이, 또는 기획서 내부 챕터 간 서술이 서로 어긋나는 부분을 발견하면
`docs/requirements/open-questions.md`에 추가하세요 — 이미 발견된 7건이 정리돼 있습니다.

## 왜 이렇게 나눘는가

`docs/adr/ADR-002-ai-agent-harness-engineering.md`에 이 구조 전체의 설계 근거가
있습니다. 요약하면: [AIBE5_FinalProject_Team6_BE(FANDROPS)](.)에서 검증된 "SSOT 계층
분리 + 경로 라우팅 + 역방향 추적 + Retro 루프" 패턴을 그대로 채용하되, 팀 구성·기간·
아키텍처·최대 리스크가 다르므로 내용은 전부 Career Dungeon에 맞게 새로 썼습니다.
