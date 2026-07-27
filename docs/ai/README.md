# docs/ai — Career Dungeon AI 에이전트 하네스

> 하네스가 뭔지 처음 접한다면 [`harness-engineering-101.md`](harness-engineering-101.md)를
> 먼저 읽으세요. 이 README는 폴더 구성과 경로 라우팅 표가 중심이고, "왜/어떻게"에 대한
> 쉬운 설명은 101 문서가 담당합니다.

이 폴더는 Claude Code(및 동일 CLI를 실행하는 IntelliJ 플러그인)가 이 저장소에서 일관되게
동작하도록 만든 문서 세트입니다. [AIBE5_FinalProject_Team6_BE(FANDROPS)](.)의 하네스 설계
(SSOT 계층 분리, 경로 라우팅, 역방향 추적, Retro 루프)를 채용했지만, 팀 구성(4명, 겸임 다수),
기간(3주), 아키텍처(단일 모놀리스), 최대 리스크(LLM 응답 불안정성)가 다르므로 내용은 전부
Career Dungeon에 맞게 새로 썼습니다. 배경과 설계 근거는
[`docs/adr/ADR-002-ai-agent-harness-engineering.md`](../adr/ADR-002-ai-agent-harness-engineering.md)에 있습니다.

## 폴더 구성

| 경로 | 역할 | 자동 로드 여부 |
| --- | --- | --- |
| `harness-engineering-101.md` | 하네스 엔지니어링 개념·구조·사용법·튜닝 건의 방법 (ELI5) | ❌ 하네스가 처음일 때 1회 |
| `SHARED.md` | 팀 전체 공통 규칙 (사고 절차, Git 컨벤션, 역방향 추적 체크리스트) | ✅ `SessionStart` 훅이 자동 주입 |
| `setup-rules.md` | Claude Code 훅/설정이 실제로 어떻게 동작하는지 검증한 사실 모음 | ❌ 훅/설정을 건드릴 때 직접 참조 |
| `owners/*.md` | 담당자별 코드 작업 규칙 (FANDROPS의 `personas/`에 대응, 이름을 바꿈) | ❌ 세션 시작 시 본인 파일만 직접 `@` |
| `workflows/` | 이슈→브랜치→PR→회고 워크플로우, 코드 리뷰 문화, 템플릿, 회고 산출물 | ❌ 필요할 때 참조 |
| `../../.claude/skills/{new-issue,new-pr}/SKILL.md` | "이슈/PR 올려줘" 자연어 요청을 위 템플릿에 채워 `gh` 명령까지 실행하는 스킬 | ✅ 자연어 요청 시 AI가 자동으로 불러옴 |

> ⚠️ **이름에 대한 주의**: 이 폴더의 `owners/`는 "AI 면접관 페르소나"가 아닙니다.
> "persona"라는 단어는 이 프로젝트에서 이미 `domain.persona`(널널한 대리 / 깐깐한 과장 등
> 면접관 성향 설정)로 선점되어 있으므로, 코드 담당자 파일에는 절대 그 이름을 쓰지 않습니다.

## 경로 → 오너 매핑 (WBS 기준)

실제 저장소 패키지 구조(`src/main/java/com/careerdungeon/...`)를 기준으로 작성했습니다.
새 코드를 작성하거나 기존 코드를 수정하기 전에, 아래 표에서 경로를 찾아 해당 owner 파일을
`@` 하세요. 경로가 겹치면 "경계가 겹치는 지점" 절을 확인하세요.

| 코드 경로 | 기획서 도메인 | 담당자 | owner 파일 |
| --- | --- | --- | --- |
| `domain/resume/**` | ① 파일 파이프라인 (이력서 업로드·파싱·저장) | 이건희 | [`owners/lee-geonhui.md`](owners/lee-geonhui.md) |
| `domain/interview/**`, `domain/message/**`, `domain/persona/**`(엔티티/설정만), `resources/prompts/persona/**`, `resources/prompts/question-generation/**` | ② 면접 엔진 + LLM 통합 | 김한비 | [`owners/kim-hanbi.md`](owners/kim-hanbi.md) |
| `domain/judgment/**`, `domain/progress/**`, `resources/prompts/scoring/**` | ③ 평가·게이지·해금 | 최용성 | [`owners/choi-yongseong.md`](owners/choi-yongseong.md) |
| `domain/auth/**`, `global/**`(common/config/exception/security/util), 프론트엔드 리포지토리 | ④ 인증 + ⑤ 인프라 + ⑥ 프론트 + 공통코드 | 표지민 | [`owners/pyo-jimin.md`](owners/pyo-jimin.md) |

### 경계가 겹치는 지점 (FANDROPS의 "협업 표"와 동일한 목적)

| 기능 | 경계 | 근거 |
| --- | --- | --- |
| 답변 제출·판정 | ②(LLM 호출까지, `interview`) → ③(루브릭 적용부터, `judgment`) | ②의 책임은 "LLM이 평가 원시값을 반환하는 시점"까지. 루브릭 적용은 ③에서만 처리 |
| 비동기 폴링/상태 조회 | ⑤(엔드포인트·응답 계약, 표지민/이건희) ↔ ②(실제 LLM 호출 상태, 김한비) | `docs/requirements/work-order.md` Phase 5 메모 — FEAT-12는 MVP 확정이지만 작업순서도에는 "후순위·하"로 표기된 불일치 있음 |
| 뱃지 지급 | ③(트리거 판정, `progress`) ↔ ①/④(가입·업로드 이벤트 발행) | `docs/requirements/functional-requirements.md` FR-09 |
| 공통 응답 포맷·예외 처리 | 표지민이 1주차 중 계약을 확정하고, 이후 3명은 그 계약만 보고 각자 구현 | `docs/ai/owners/pyo-jimin.md`의 "위임 규칙" 참고 — 표지민이 매번 병목이 되지 않도록 하네스가 방어 |

이 표에 없는 경로를 수정해야 한다면, 팀 채널에 먼저 공지하고 이 표와 각 owner 파일의
`paths`를 함께 갱신하세요.

## 설계 문서 전체 지도 (`docs/` SSOT)

owner 파일의 "추가 필수 참조"에 없는 배경 문서가 필요하면 아래에서 찾으세요. 전체
인덱스는 [`docs/README.md`](../README.md)입니다.

| 궁금한 것 | 문서 |
| --- | --- |
| 상태값 전이(`parseStatus`, `InterviewSession.status`), 레벨/뱃지 불변식 | [`docs/state/invariants-and-state-machines.md`](../state/invariants-and-state-machines.md) |
| LLM 비용 상한, Mock 모드 정책 | [`docs/operations/llm-cost-policy.md`](../operations/llm-cost-policy.md) |
| 실패 처리(파일/LLM/인증), 일정 리스크 대응 | [`docs/operations/failure-policy.md`](../operations/failure-policy.md) |
| 아키텍처/벤더/통신방식/인증 선택 근거 | [`docs/adr/README.md`](../adr/README.md) (ADR-001, 003~006) |
| 현재 팀이 아직 확정하지 못한 항목 전체 목록 | [`docs/requirements/open-questions.md`](../requirements/open-questions.md) |
| Refresh Token 만료 등 구체적인 보안 정책 값 | [`docs/requirements/security-design.md`](../requirements/security-design.md) |
| 4주 마일스톤(도메인×주차), 테스트 전략 | [`docs/requirements/milestones.md`](../requirements/milestones.md), [`docs/requirements/test-strategy.md`](../requirements/test-strategy.md) |
| AI 리뷰(CodeRabbit)와 사람 리뷰 역할 분담, 셀프 리뷰, 이모지 룰 | [`workflows/code-review-culture.md`](workflows/code-review-culture.md) |
| ADR을 왜/언제/어떻게 쓰는지, 자동 감지 훅 | [`docs/adr/how-to-write-adr.md`](../adr/how-to-write-adr.md) |

## 세션을 시작할 때 (팀 규칙)

1. `SessionStart` 훅이 `docs/ai/SHARED.md`를 자동으로 컨텍스트에 주입합니다. 아무것도 하지 않아도 됩니다.
2. 본인 owner 파일 1개만 직접 `@docs/ai/owners/<본인 이름>.md`로 멘션하세요.
3. 다른 사람의 owner 파일은 "손대지 말 것" 절을 확인하는 용도로만 참고하고, 그 사람의 경로를 직접 수정하지 마세요.
4. 세션을 마칠 때 SHARED.md의 `[Retro]` 한 줄 형식으로 출력하세요. 파일로 저장하지 않습니다
   (금요일 주간 압축은 `workflows/auto-pr.md` 참고).
