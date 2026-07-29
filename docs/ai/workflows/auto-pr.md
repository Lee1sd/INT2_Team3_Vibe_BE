# auto-pr.md — 이슈 → 브랜치 → PR → 회고 워크플로우

AI 에이전트(Claude Code)가 기능 작업을 진행할 때 따르는 표준 흐름입니다. FANDROPS와 동일한
패턴(본문 골격은 템플릿으로 별도 보관하고, `--body-file`은 채운 파일만 사용)을 채용합니다.

> **가장 쉬운 방법**: 아래 1번과 5번은 각각 `/new-issue`, `/new-pr` 스킬이 대신
> 해줍니다. Claude Code에서 "이슈 올려줘" / "PR 올려줘"라고 자연어로 요청하면
> 스킬이 자동으로 실행되고, 직접 `/new-issue`, `/new-pr`로 호출할 수도 있습니다
> (`.claude/skills/new-issue/SKILL.md`, `.claude/skills/new-pr/SKILL.md`, 검증
> 상태는 `docs/ai/setup-rules.md` §7 참고). 아래 단계는 스킬이 내부적으로 따르는
> 순서이자, 스킬 없이 수동으로 할 때의 순서이기도 합니다.

## 흐름

1. **이슈 생성 (필요한 경우)**
   - `docs/ai/workflows/templates/issue-feature-body.md`(기능) 또는
     `issue-bug-body.md`(버그)를 복사해 내용을 채웁니다.
   - `gh issue create --title "[FEAT] ..." --body-file <채운 파일> --label enhancement --assignee <GitHub 아이디>`
   - **담당자 없는 이슈로 브랜치를 만들지 않습니다.** 작업을 시작하는 시점에는 담당자가
     반드시 지정돼 있어야 합니다 (`docs/ai/SHARED.md` §4-5 이슈 트래킹 규칙).
   - 당장 착수하지 않는 **백로그성 이슈**만 등록 시 담당자 미정을 허용합니다. 이때는
     `--assignee`를 빼고 만들되, 본문에 우선순위 낮음과 착수 절차를 반드시 적습니다.
     - `gh issue create --title "[CHORE] ..." --body-file <채운 파일> --label chore`
       (`--assignee` 없음)
     - 본문에 남길 문장: "우선순위 낮음 — 착수하는 사람이
       `gh issue edit <번호> --add-assignee <GitHub 아이디>`로 자신을 지정한 뒤 시작한다."
     - 착수 시 `gh issue edit <번호> --add-assignee <GitHub 아이디>`로 지정한 **뒤에**
       브랜치를 만듭니다.
   - GitHub UI의 `.github/ISSUE_TEMPLATE/feature.md`, `bug.md`와 구조는 동일하되,
     자동화 스크립트에서 대화형 입력 없이 바로 채울 수 있도록 템플릿을 별도로 둡니다.
2. **브랜치 생성**
   - `feat/{이슈번호}-{기능명}` 또는 `fix/{이슈번호}-{내용}` — `{이슈번호}`는 1번에서 만든
     이슈 번호와 반드시 일치해야 합니다.
   - 예: `feat/23-resume-upload`
3. **작업**
   - `docs/ai/SHARED.md`의 6단계 사고 절차를 따릅니다.
   - 커밋은 논리적 단위로 나누고, 접두사(`feat`/`fix`/`refactor`/`docs`/`test`/`chore`) +
     "왜"를 남깁니다 (`docs/ai/SHARED.md` §4-2).
   - 구조적 결정(대안 검토, 다른 도메인 영향 등)을 했다면 `docs/adr/how-to-write-adr.md` §3
     기준으로 ADR이 필요한지 확인합니다. `.claude/hooks/adr-suggestion.py`가 관련 파일을
     건드리거나 결정 키워드를 감지하면 리마인더를 띄워줍니다(참고용, 강제 아님).
4. **PR 생성 전 셀프 리뷰**
   - `docs/ai/workflows/code-review-culture.md` §3-1 체크리스트로 diff를 스스로 먼저 읽습니다.
5. **PR 생성**
   - `docs/ai/workflows/templates/pr-body.md`를 복사해 내용을 채웁니다.
   - `gh pr create --title "..." --body-file <채운 파일>` — 이 명령을 실행하면
     `adr-suggestion.py`가 ADR 필요 여부를 한 번 더 상기시켜줍니다.
   - 본문 구조는 `.github/PULL_REQUEST_TEMPLATE.md`와 동일합니다.
   - **최소 1인의 리뷰 승인이 있어야 머지합니다.** 승인 전에는 머지하지 않습니다
     (`docs/ai/SHARED.md` §4-3, 기획서 12장 "도메인 코드 고립" 리스크 대응).
6. **리뷰 반영**
   - AI(CodeRabbit) 코멘트와 사람 코멘트의 역할 분담, 이모지 룰은
     `docs/ai/workflows/code-review-culture.md`를 따릅니다.
   - 리뷰 코멘트는 담당 owner 파일의 체크리스트에 빠진 항목이 있는지 먼저 확인하고,
     빠진 항목이 있었다면 owner 파일에 추가할지 그 주 금요일 회고에서 논의합니다.
7. **머지 후 회고**
   - `docs/ai/SHARED.md` §5 형식의 `[Retro]` 한 줄을 세션 종료 시 출력합니다.
     `feat/*`/`fix/*` 브랜치라면 `.claude/hooks/retro-reminder.py`가 이걸 자동으로
     감지해 개인 로컬 `docs/ai/workflows/generated/retro-raw.md`에 쌓아주므로,
     사람이 따로 파일에 옮겨 적을 필요는 없습니다.
   - 매주 금요일, 4명 각자의 `retro-raw.md`를 공유하고
     `docs/ai/workflows/templates/retro-week-body.md` 템플릿으로 카테고리별 분류·정리해
     `docs/ai/workflows/generated/retro-week{N}.md` 1개 파일로 압축합니다. 절차는
     `docs/ai/workflows/generated/README.md`를 따릅니다.

## 템플릿과 실제 GitHub 템플릿의 차이

| | `.github/ISSUE_TEMPLATE/*.md`, `PULL_REQUEST_TEMPLATE.md` | `docs/ai/workflows/templates/*.md` |
| --- | --- | --- |
| 용도 | GitHub 웹 UI에서 사람이 이슈/PR을 만들 때 자동으로 뜨는 폼 | AI/CLI가 `gh ... --body-file`로 프로그래밍적으로 채워 넣는 원본 |
| 형식 | YAML frontmatter 포함 (`name`, `about`, `title`, `labels`) | frontmatter 없음, 본문 구조만 |
| 수정 시 주의 | 두 구조(제목·섹션 순서)는 항상 동일하게 유지 — 하나만 고치고 다른 하나를 잊지 않는다 | |

## 스킬과 수동 절차의 관계

| | `/new-issue`, `/new-pr` 스킬 | 위 1~5번 수동 절차 |
| --- | --- | --- |
| 언제 쓰나 | 평소 기본값 — "이슈/PR 올려줘"라고만 말하면 됨 | 스킬이 없거나 실패했을 때, 또는 스킬이 자동 실행되지 않는 CLI/환경 |
| 담당자·셀프 리뷰·ADR 확인 | 스킬 지침에 이미 내장되어 있어 빠뜨릴 수 없음 | 사람(또는 AI)이 매 단계를 직접 챙겨야 함 |
| 최종 확인 | `gh issue/pr create` 실행 전 채운 본문을 반드시 미리 보여주고 확인받음 | 동일하게 사람이 직접 확인 |

## generated/ 폴더 정책

- `docs/ai/workflows/generated/retro-week{N}.md`는 **커밋 대상입니다.** (FANDROPS와
  달리 파일 수가 주 단위로 최대 4개까지만 쌓이므로 `.gitignore`에 넣지 않습니다.)
- 같은 폴더의 `retro-raw.md`는 예외입니다 — 개인 로컬 원본이라 `.gitignore` 대상입니다
  (자세한 이유는 `docs/ai/workflows/generated/README.md`).
- 이슈/PR 본문 임시 파일(작성 중간 산물)은 이 폴더에 두지 않습니다. `.tmp-issue-body.md`,
  `.tmp-pr-body.md`처럼 저장소 루트에 두고 `gh` 명령 실행 후 삭제합니다
  (`.claude/skills/new-issue/`, `new-pr/` 참고).
