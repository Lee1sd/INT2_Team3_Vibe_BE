# AGENTS.md — Career Dungeon Backend 진입점

이 파일은 Codex가 프로젝트 작업 시 읽는 루트 진입점입니다.
규칙 본문은 이 파일에 중복해서 작성하지 않고,
다음에 읽어야 할 프로젝트 문서만 안내합니다.

## 언어 및 커밋 규칙 요약

**⚠️ 모든 커밋 메시지는 반드시 `<type>: <한국어 요약>` 형식으로 작성한다
(예: `feat: 질문 생성 세션 검증 강화`). 영어 커밋 메시지는 금지한다.**

## 하네스가 처음이라면

하네스 구조와 사용법을 이해하려면 가장 먼저
`docs/ai/harness-engineering-101.md`를 읽습니다.

## 언어

모든 응답, 커밋 메시지, PR/이슈 제목과 본문, 코드 주석은
한국어로 작성한다. 영어로 작성하지 않는다.

## 모든 작업의 필수 읽기 순서

1. `docs/ai/SHARED.md`
  - 팀 전체 공통 규칙
  - 작업 사고 절차
  - Git 규칙
  - 역방향 추적 체크리스트
  - LLM 응답 방어 규칙

2. 현재 작업자 또는 담당 도메인의 owner 문서
  - `docs/ai/README.md`의 경로 → 오너 표를 확인한다.
  - 해당하는 `docs/ai/owners/<이름>.md`를 읽는다.
  - 사용자가 담당자 이름을 지정했다면 그 사람의 owner 문서를 우선한다.

3. owner 문서의 “추가 필수 참조”에 나온 설계 문서
  - 필요한 절만 읽는다.
  - 관련 요구사항, API, ERD, 상태 머신, ADR, 운영 정책을 확인한다.

4. `docs/requirements/open-questions.md`
  - 관련 항목이 이미 확정되었는지 확인한다.
  - 확정된 값을 임의로 TBD 또는 임시값으로 되돌리지 않는다.

## 작업 유형별 규칙

### 기능 구현 또는 수정

- 구현 전에 관련 SSOT 문서를 확인한다.
- 코드와 문서가 충돌하면 임의로 결정하지 말고 충돌 내용을 보고한다.
- 담당 owner의 허용 경로와 금지 경로를 준수한다.
- 구현 후 관련 테스트와 빌드를 실행한다.
- 구조적 결정이 포함되면 ADR 필요 여부를 검토한다.

### PR/이슈 작성

- PR을 만들 때는 항상 .github/PULL_REQUEST_TEMPLATE.md 형식을
  따른다 (작업 개요, 변경 사항, 관련 이슈, 테스트, 체크리스트,
  API 변경 사항, 리뷰 포인트, 역방향 추적 6문항 순서)
- 이슈를 만들 때는 .github/ISSUE_TEMPLATE/feature.md 또는
  bug.md 형식을 따른다
- 작업 단위가 끝날 때마다 커밋한다 (여러 작업을 하나의 커밋으로
  뭉치지 않는다)

## PR/이슈 본문 작성 시 주의

한글이 포함된 PR/이슈 본문은 파이프(-)로 직접 넘기지 말고,
반드시 UTF-8 임시 마크다운 파일로 먼저 작성한 뒤
--body-file 옵션으로 전달한다.

### 코드 리뷰

- 사용자가 리뷰 범위를 따로 지정하면 그 범위를 우선한다.
- 별도 지정이 없으면 main 대비 현재 브랜치의 diff를 중심으로 본다.
- Critical과 Informational을 구분한다.
- 숫자, 필드명, 제한값, 재시도 횟수가 SSOT와 일치하는지 확인한다.
- PR 본문에 이미 "범위 밖"으로 명시된 항목을 발견하면 새 지적사항(Full review comments)으로
  올리지 않는다. 대신 리뷰 요약 첫 줄에
  "참고: PR 본문에 이미 명시된 범위 밖 항목 X건 확인, 재지적 안 함"이라고 한 줄만 남긴다.

### LLM 관련 코드

LLM 직접 호출 또는 다음 경로를 수정하기 전에
`docs/ai/SHARED.md`의 LLM 응답 방어 규칙과 담당 owner 체크리스트를 확인한다.

- interview
- judgment
- progress
- persona
- prompts

Mock 모드를 기본값으로 유지하며 다음을 확인한다.

- JSON 스키마 검증
- 파싱 실패 처리
- 재시도 정책
- 점수 범위 클램핑
- 타임아웃 및 폴백
- 모델명 하드코딩 여부

## AI 실험/트러블슈팅 기록

- 프롬프트/모델/채점 등 서비스 Claude API 활용 관련 시행착오는
  `docs/ai/ai-experiment-log.md`에 기록한다.
- Claude Code/Codex 하네스 사용 중 겪은 문제(SSOT 검증,
  리뷰 반영 오염 방지 등)는 `docs/ai/harness-troubleshooting.md`에
  기록한다.
- 회고를 기다리지 않고 사건 발생 직후 기록한다.
- 단순 오타/변수명 수정은 기록하지 않는다.

## 지금 바로 확인할 문서

| 목적 | 문서 |
| --- | --- |
| 경로별 담당자 확인 | `docs/ai/README.md` |
| 공통 작업 규칙 | `docs/ai/SHARED.md` |
| 하네스 사용법 | `docs/ai/harness-engineering-101.md` |
| 이슈·브랜치·PR 흐름 | `docs/ai/workflows/auto-pr.md` |
| 코드 리뷰 문화 | `docs/ai/workflows/code-review-culture.md` |
| ADR 작성 기준 | `docs/adr/how-to-write-adr.md` |
| 하네스 설계 근거 | `docs/adr/ADR-002-ai-agent-harness-engineering.md` |

## 절대 하지 말 것

- `docs/ai/owners/*.md`를 AI 면접관 페르소나 설정으로 해석하지 않는다.
- 관련 문서를 읽지 않고 API 필드명, 상태값, 점수 기준을 새로 만들지 않는다.
- `open-questions.md`에서 이미 확정된 내용을 임의로 변경하지 않는다.
- 사용자 확인 없이 대규모 범위 변경 또는 다른 담당자의 코드까지 수정하지 않는다.

