# setup-rules.md — Claude Code 훅/설정 사실 검증 문서

Claude Code 관련 정보는 매우 빠르게 바뀌고, 오래된/부정확한 블로그 글이 많습니다.
FANDROPS도 같은 이유로 이 문서를 팀 내부에 두었습니다. 이 문서는 **우리가 실제로 확인한 사실만**
적습니다. 확인하지 못한 내용은 "미확인"이라고 명시하고, 인터넷 검색 결과를 그대로 믿지 않습니다.

이 문서와 실제 동작이 어긋나는 걸 발견하면, 이 문서를 바로 고치고 왜 고쳤는지 한 줄을
남기세요 (Retro 루프의 일부입니다).

## 1. SessionStart 훅은 실제로 존재하고 컨텍스트 자동 주입이 가능하다

FANDROPS의 ADR-004는 "훅으로 SHARED.md를 자동 주입할 수 없다"고 결론 내리고, 매 세션 사람이
직접 `@docs/ai/SHARED.md`를 입력하는 것을 팀 규칙(Compliance 01)으로 강제했습니다.
공식 문서 기준으로 이 전제는 더 이상 유효하지 않습니다.

- `SessionStart` 이벤트가 존재하며, 새 세션 시작·`--continue`/`--resume`으로 세션을 이어갈 때,
  그리고 `/clear`로 컨텍스트를 비운 뒤에도 실행됩니다.
- 훅 스크립트가 stdout으로 출력한 텍스트, 또는 JSON으로
  `{"hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": "..."}}`
  형식을 출력하면 해당 텍스트가 세션 컨텍스트에 자동으로 주입됩니다.
- 이 프로젝트는 이 방식으로 `docs/ai/SHARED.md`를 자동 주입합니다
  (`.claude/hooks/session-context.py`). **owner 파일(`docs/ai/owners/*.md`)은 자동 주입하지
  않습니다** — 담당자가 매 세션 달라질 수 있고, 4개 파일을 전부 주입하면 다른 담당자 규칙까지
  컨텍스트에 섞여 오히려 혼란을 줄 수 있기 때문에 의도적으로 제외했습니다.

## 2. PostToolUse 훅은 도구 실행 이후, matcher로 도구를 필터링할 수 있다

- `matcher` 필드에 `Edit|Write|MultiEdit`처럼 파이프로 여러 도구 이름을 지정할 수 있습니다.
- 훅 스크립트는 stdin으로 도구 입력(JSON, 수정된 파일 경로 포함)을 받습니다.
- 이 프로젝트는 `interview/`, `judgment/`, `progress/`, `persona/` 경로 수정을 감지해
  "LLM 응답 방어 체크리스트"를 다시 상기시키는 용도로만 사용합니다
  (`.claude/hooks/llm-json-guard.py`). 코드를 자동으로 막거나 되돌리지는 않습니다 —
  차단성 훅은 팀이 아직 필요성에 합의하지 않았습니다.

## 3. Stop 훅은 에이전트 턴이 끝날 때 실행된다

- 사람이 응답을 기다리는 상태로 전환되기 직전에 실행됩니다.
- 이 프로젝트는 현재 브랜치 이름이 `feat/*`/`fix/*`일 때만 동작하는 용도로 사용합니다
  (`.claude/hooks/retro-reminder.py`). 이 훅도 차단성이 아닙니다.
- **stdin JSON에 `last_assistant_message`(방금 AI가 낸 마지막 응답 전체 텍스트)가
  들어옵니다 (검증됨).** 이 훅은 그 안에서 `[Retro] ... Edge Case: ...` 블록을
  정규식으로 찾아서, 찾으면 `docs/ai/workflows/generated/retro-raw.md`에 자동으로
  append하고, 못 찾으면 기존처럼 형식을 리마인드합니다 — 즉 AI가 `[Retro]`를 이미
  출력했다면 사람이 채팅 기록에서 복붙할 필요가 없습니다 (자세한 검증 내용은
  §3-2, "왜 이렇게 바꿨는지"는 `docs/adr/ADR-002-ai-agent-harness-engineering.md`
  결정 이력 참고).
- stdin에는 `stop_hook_active`도 들어오는데, 이건 "이전에 Stop 훅이 차단(exit 2)해서
  AI가 재시도 중"일 때만 `true`가 됩니다. 이 프로젝트의 어떤 훅도 차단하지 않으므로
  실제로는 항상 `false`겠지만, 나중에 차단성 훅을 추가할 경우를 대비해 방어적으로
  `true`면 즉시 반환하도록 만들어뒀습니다.
- **한계**: `retro-raw.md`는 브랜치가 아니라 로컬 저장소 단위로 쌓입니다 — 4명이
  각자 자기 PC에서 작업하므로 이 파일은 애초에 팀 공유 파일이 아니라 "개인 원본"
  입니다. `.gitignore`에 등록한 이유이기도 합니다 (§6 표, `docs/ai/workflows/generated/README.md`).

## 3-1. 하나의 PostToolUse 이벤트에 matcher를 여러 개 등록할 수 있다 (검증됨)

- `PostToolUse` 배열에 `matcher`가 다른 블록을 여러 개 추가하면, 각 블록이 독립적으로
  매칭·실행됩니다. 이 프로젝트는 `Edit|Write|MultiEdit` 블록(코드 수정 감지용)과
  `Bash` 블록(`gh pr create`/`gh issue create` 명령 감지용)을 따로 등록하고, 둘 다
  `.claude/hooks/adr-suggestion.py` 하나를 실행합니다 — 스크립트가 `tool_input`의
  모양(`command` 키 유무)으로 어느 쪽 트리거인지 스스로 구분합니다.
- 같은 `matcher` 블록 안에서도 `hooks` 배열에 스크립트를 여러 개 넣을 수 있습니다
  (예: `Edit|Write|MultiEdit`에 `llm-json-guard.py`와 `adr-suggestion.py`를 둘 다 등록).
  각 스크립트는 독립적으로 stdin을 받고 독립적으로 `additionalContext`를 출력합니다.
- `.claude/hooks/adr-suggestion.py`를 `py -3 -c`로 stdin을 시뮬레이션해 6가지
  케이스(gh pr/issue create, 일반 bash, 구조 파일 경로 수정, 결정 키워드 포함 수정,
  중립적인 수정)로 실제 검증했습니다 — 의도한 4가지 케이스에서만 리마인더가
  출력되고, 나머지 2가지(일반 bash, 중립적 수정)에서는 아무 출력도 없음을 확인.

## 3-2. `retro-reminder.py`의 `[Retro]` 자동 추출·저장을 실제로 검증했다 (검증됨)

- `extract_retro_block()`을 실제 한국어 `[Retro]` 블록이 포함된 응답 문자열로
  호출해, 블록 앞뒤에 다른 텍스트가 있어도 `[Retro]`부터 `Edge Case: ...` 줄까지만
  정확히 잘라내는지 확인했습니다. 블록이 없는 입력에는 빈 문자열을 반환합니다.
- `append_to_raw_log()`을 임시 폴더에 두 번 호출해(서로 다른 브랜치/작성자), 첫
  호출 시 안내 헤더가 한 번만 생기고, 두 항목이 구분자(`---`)로 나뉘어 순서대로
  쌓이는지, 그리고 한글이 `encoding="utf-8"`로 깨지지 않고 저장되는지 확인했습니다.
- `main()` 전체를 stdin을 시뮬레이션해 4가지 케이스로 검증했습니다: ①
  `feat/*` 브랜치 + `[Retro]` 있음 → 자동 저장 + 저장 안내, ② `main` 브랜치 →
  아무 출력 없음, ③ `feat/*` 브랜치 + `[Retro]` 없음 → 기존 리마인더 출력, ④
  `stop_hook_active: true` → 즉시 반환(무한 루프 방지). 네 케이스 모두 의도한
  대로 동작했습니다.
- **아직 검증 못 한 부분**: 이건 함수 단위 테스트이고, 실제 `claude` CLI 세션에서
  AI가 `[Retro]`를 출력한 뒤 Stop 훅이 진짜로 파일을 만드는지는 팀 PC에서
  확인이 필요합니다. Windows에서 `git` 명령이 없는 환경(§5)에서는
  `current_branch()`가 빈 문자열을 반환해 이 훅 전체가 조용히 아무 것도 안 하니,
  먼저 `git --version`이 되는지 확인하세요.

## 4. IntelliJ Claude Code 플러그인은 별도 하네스가 필요 없다

- 플러그인은 자체 CLI를 내장하지 않고, 로컬에 설치된 `claude` 바이너리를 그대로 실행합니다.
- `.claude/settings.json`, `hooks/`, 루트 `CLAUDE.md`는 CLI로 실행하든 IntelliJ 플러그인으로
  실행하든 **동일하게 적용됩니다.** 플러그인 전용 하네스를 별도로 만들 필요가 없습니다.
- 플러그인 자체 설정(모델 선택, diff 도구, permission mode)은
  `Settings → Tools → Claude Code`에서 개인별로 설정하며, 이건 IDE 개인 설정이므로
  리포지토리에 커밋하지 않습니다.

## 5. Windows에서 흔한 함정

- 훅 커맨드에 `python3`을 쓰면 Windows에서 실행 파일을 못 찾는 경우가 흔합니다.
- **실제로 겪은 함정 (확인됨, 2026-07-14 retro로 재확인)**: Windows에 Python을 표준
  설치하지 않고 `py` 런처만 있는 PC에서는 `python`이
  `C:\Users\<사용자>\AppData\Local\Microsoft\WindowsApps\python.exe`
  (Microsoft Store 앱 실행 별칭 스텁)로 연결되어 아무 동작도 하지 않고 조용히 종료됩니다.
  즉 훅이 "실행은 됐지만 아무 출력도 없는" 상태가 되어 원인을 찾기 어렵습니다. 이 때문에
  팀원 다수의 retro 훅이 조용히 작동하지 않고 있던 것이 확인되어, 이 프로젝트
  `.claude/settings.json`의 훅 커맨드 기본값을 `python`에서 **`py -3`으로 변경**했습니다.
  그래도 팀원 PC에서 `py -3 --version`이 안 된다면(예: `py` 런처조차 없는 설치) `설정 →
  앱 실행 별칭`을 먼저 확인하고, `.claude/settings.local.json`으로 개인 환경에 맞게
  오버라이드하세요.
- PowerShell 실행 정책 때문이 아니라 훅은 `python <스크립트 경로>` 형태로 직접 인터프리터를
  호출하므로, 스크립트 자체에 실행 권한이 없어도 동작합니다.

## 6. 커밋 대상 vs 개인 설정

| 파일 | 커밋 여부 | 이유 |
| --- | --- | --- |
| `.claude/settings.json` | ✅ 커밋 | 팀 공용 훅·프로젝트 설정 |
| `.claude/settings.local.json` | ❌ `.gitignore` | 개인 permission 캐시 (승인한 도구 목록 등) |
| `.claude/skills/*/SKILL.md` | ✅ 커밋 | 팀 공용 스킬(§7) — `docs/ai/workflows/templates/*`와 함께 SSOT |
| `.tmp-issue-body.md`, `.tmp-pr-body.md` | ❌ `.gitignore` | 스킬이 `gh issue/pr create` 전에 잠깐 쓰는 임시 본문 |
| `docs/ai/workflows/generated/retro-week*.md` | ✅ 커밋 | 팀의 하네스 튜닝 근거 기록 (발표 자료로도 사용) |
| `docs/ai/workflows/generated/retro-raw.md` | ❌ `.gitignore` | `retro-reminder.py`가 세션마다 쌓는 개인 로컬 원본 — 4명이 동시에 커밋하면 매 PR마다 충돌 |

## 7. Skills(`.claude/skills/`)로 이슈/PR 생성을 자동화할 수 있다

Claude Code의 커스텀 슬래시 커맨드는 2026년 기준 **Skills**로 통합되었습니다
(`.claude/commands/*.md` 레거시 형식도 계속 동작하지만, 이 프로젝트는 신규 형식인
`.claude/skills/<이름>/SKILL.md`를 사용합니다). 공식 문서(`code.claude.com/docs/en/slash-commands`)
기준으로 확인한 사실이며, **팀 PC에서 `claude` CLI로 실제 트리거해본 검증은 아직
하지 않았습니다** — 처음 써보는 팀원은 아래 "확인 방법"으로 먼저 검증하세요.

- 폴더 이름이 곧 커맨드 이름이 됩니다: `.claude/skills/new-issue/SKILL.md` →
  `/new-issue`. 이 프로젝트는 `new-issue`, `new-pr` 두 스킬을 둡니다.
- `SKILL.md` 맨 위 YAML frontmatter의 `description`을 Claude가 항상 컨텍스트에
  들고 있다가, 사용자가 "이슈 올려줘"/"PR 올려줘"처럼 description과 비슷한 요청을
  하면 **슬래시 커맨드를 직접 치지 않아도 스스로 그 스킬을 불러와 실행**합니다.
  물론 `/new-issue`, `/new-pr`로 직접 호출할 수도 있습니다.
- `` !`명령어` `` 문법은 Claude가 스킬 내용을 보기 **전에** 셸 명령을 먼저 실행해
  그 출력을 스킬 본문에 끼워 넣습니다 (동적 컨텍스트 주입). `new-pr`은 이 방식으로
  `git branch`/`git status`/`git diff --stat`/`git log` 결과를 미리 모아서
  Claude에게 넘기므로, PR 본문을 지어내지 않고 실제 변경사항을 근거로 채웁니다.
- `allowed-tools`에 등록한 도구(예: `Bash(gh pr create *)`)는 그 스킬이 실행되는
  동안 매번 승인을 묻지 않고 바로 실행됩니다. 단, `gh issue create`/`gh pr create`
  자체는 되돌리기 번거로운 부수효과이므로, 두 스킬 모두 **실행 직전에 채운 본문을
  사용자에게 먼저 보여주고 확인받도록 지침에 명시**해 두었습니다 — `allowed-tools`
  승인과 별개로, 스킬 지침 수준에서 한 번 더 안전장치를 둔 것입니다.
- 두 스킬은 `docs/ai/workflows/templates/issue-feature-body.md`,
  `issue-bug-body.md`, `pr-body.md`를 그대로 읽어서 채웁니다 — 템플릿을 스킬
  안에 복제하지 않으므로, 템플릿을 고치면 스킬도 자동으로 최신 내용을 따라갑니다.

### 확인 방법 (팀원이 처음 써볼 때)

1. 아무 파일이나 조금 수정하고 `git status`로 변경사항이 있는 상태를 만듭니다.
2. `claude`를 실행하고 `/new-pr`이라고만 입력하거나, "PR 올려줘"라고 자연어로
   요청합니다.
3. `gh pr create`를 실행하기 전에 채운 본문 미리보기가 나오는지 확인합니다. 실제로
   PR을 만들 필요가 없다면 이 단계에서 "취소"라고 답하면 됩니다.
4. 스킬이 전혀 반응하지 않으면 `/doctor`로 스킬 목록에 `new-issue`/`new-pr`이
   보이는지 확인하고, 이 문서를 "미확인 → 실패 사례"로 갱신하세요.

## 8. 미확인 항목 (섣불리 믿지 말 것)

- `PreCompact` 훅으로 컨텍스트 압축 직전에 무언가를 주입하는 방식은 이 프로젝트에서
  아직 실제로 테스트하지 않았습니다. 필요해지면 이 문서에 검증 결과를 추가하세요.
- 훅 실행 타임아웃, 동시 실행 시 순서 보장 여부는 팀이 직접 확인하기 전까지 "미확인"으로
  둡니다.
- §7의 `new-issue`/`new-pr` 스킬은 공식 문서 기준으로 작성했지만, 팀 PC에서
  `claude` CLI로 실제 트리거해 "자연어 요청만으로 스킬이 자동 실행되는지"까지는
  아직 검증하지 않았습니다. 처음 써본 팀원이 위 "확인 방법"을 거친 뒤 이 항목을
  "검증됨"으로 갱신해주세요.
- §3-2의 `retro-reminder.py` 자동 저장도 마찬가지로 함수 단위로만 검증했습니다.
  실제 `claude` CLI가 `last_assistant_message`를 정확히 채워주는지, 팀 PC에
  `git`이 PATH에 있는지는 실사용 중 첫 번째 `feat/*` 세션을 마친 뒤
  `docs/ai/workflows/generated/retro-raw.md`가 실제로 생겼는지 확인해서 검증하세요.
