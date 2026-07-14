# 훅(Hook) 로컬 동작 확인 절차

> 배경: 2026-07-14 retro 점검 중 `.claude/hooks/*.py` 4개가 팀원 로컬 환경에서
> 조용히 실패하고 있던 것을 발견했습니다 (원인은 `docs/adr/ADR-002-ai-agent-harness-engineering.md`
> 결정 이력의 2026-07-14 항목 참고). 이 문서는 **각자 로컬에서 훅이 실제로 동작하는지
> 스스로 확인하는 절차**입니다. 4명 전원이 한 번씩 따라 해보고, 실패하는 단계가 있으면
> 팀 채널에 공유하세요 — 조용히 실패하는 게 이 문제의 핵심이라 본인이 직접 확인하지
> 않으면 아무도 모릅니다.

## 사전 준비

가장 먼저 최신 `main`(또는 이 문서가 포함된 브랜치)을 pull 받아, 아래 수정사항이
로컬에 반영돼 있는지 확인하세요.
- `.claude/settings.json`의 훅 커맨드가 `py -3 ...`로 되어 있는지
  (`python ...`이면 아직 구버전입니다)
- `.claude/hooks/retro-reminder.py`, `session-context.py`, `llm-json-guard.py`,
  `adr-suggestion.py` 4개 파일에 `sys.stdin.reconfigure(encoding="utf-8")`가
  있는지 (`main()` 함수 상단, `json.load(sys.stdin)` 바로 위)

## 1단계 — Python 실행 환경 확인

터미널(PowerShell)에서:

```powershell
py -3 --version
```

**기대 결과**: `Python 3.x.x` 형태로 버전이 찍힘.

**실패 시**: "Python was not found..." 같은 메시지가 뜨거나 Microsoft Store가
열리면, `py` 런처 자체가 없는 것입니다. `설정 → 앱 실행 별칭`에서
`python.exe`/`python3.exe` 별칭을 끄고, python.org에서 Python을 표준 설치하세요.
자세한 내용은 `docs/ai/setup-rules.md` §5 참고.

## 2단계 — 훅 4개를 직접 호출해서 크래시 여부 확인

프로젝트 루트에서 아래 4개를 순서대로 실행하세요. **한글이 섞인 입력**으로
테스트하는 게 핵심입니다 (영어만 넣으면 인코딩 버그가 안 드러남).

```powershell
# 2-1) session-context.py
'{}' | py -3 .claude/hooks/session-context.py
# 기대: JSON 한 줄 출력, 에러 없음(exit code 0)

# 2-2) llm-json-guard.py
'{"tool_input": {"file_path": "domain/interview/Test.java", "new_string": "점수 검증 테스트"}}' | py -3 .claude/hooks/llm-json-guard.py
# 기대: "[LLM 응답 방어 체크리스트 ...]" JSON 출력, 에러 없음

# 2-3) adr-suggestion.py
'{"tool_input": {"file_path": "application.yml", "new_string": "대안을 검토해 채택했습니다"}}' | py -3 .claude/hooks/adr-suggestion.py
# 기대: "[ADR 제안 ...]" JSON 출력, 에러 없음

# 2-4) retro-reminder.py — 반드시 feat/ 또는 fix/ 브랜치에서 실행해야 동작함
git branch --show-current
# feat/* 나 fix/* 브랜치가 아니면 임시로 하나 만드세요: git checkout -b feat/000-hook-check
'{"last_assistant_message": "테스트 응답입니다.\n\n[Retro] 룰 작동: 테스트\n       | 튜닝 제안: 없음\n       | SSOT 동기화 필요: 없음\n       | Edge Case: 없음"}' | py -3 .claude/hooks/retro-reminder.py
# 기대: stderr에 "[retro-reminder] 이번 세션의 [Retro]를 ... 자동 저장했습니다." 출력
```

**실패 시(`UnicodeEncodeError`/`UnicodeDecodeError` 트레이스백이 뜸)**: 1단계는
통과했지만 stdin 인코딩 버그입니다. `.claude/hooks/*.py`가 최신인지 다시 확인하세요
(pull 안 됐을 가능성).

**아무 출력도 없고 그냥 조용히 끝남**: `py -3`이 아니라 `python`으로 잘못
실행했거나, `.claude/settings.json`이 구버전일 가능성이 있습니다.

## 3단계 — retro-reminder.py는 파일에 실제로 쌓였는지 확인

2-4를 실행했다면:

```powershell
Get-Content docs/ai/workflows/generated/retro-raw.md -Tail 10
```

방금 넣은 `## 날짜 · 브랜치 · 작성자` 블록이 파일 맨 아래 보이면 정상입니다.
테스트용으로 넣은 내용이니 확인 후 지우거나(파일 자체가 `.gitignore` 대상이라
지워도 커밋에 영향 없음) 그대로 둬도 됩니다. 임시 브랜치를 만들었다면
`git checkout <원래 브랜치>` 후 `git branch -D feat/000-hook-check`로 정리하세요.

## 4단계 — 실제 Claude Code 세션에서 확인 (가장 중요)

수동 테스트가 다 통과했다면, 실제 작업 흐름에서도 확인하세요.

1. `feat/*` 또는 `fix/*` 브랜치에서 Claude Code 새 세션을 시작합니다.
2. 아무 작업이나 하나 요청하고, 끝날 때 `SHARED.md` §6 절차대로
   `[Retro] 룰 작동: ... | 튜닝 제안: ... | SSOT 동기화 필요: ... | Edge Case: ...`
   형식이 응답 마지막에 출력되는지 확인합니다(AI가 안 내면 직접 "Retro 출력해줘"라고
   요청하세요).
3. 응답이 끝난 직후 `[retro-reminder] ... 자동 저장했습니다.` 메시지가 보이는지
   확인합니다(Claude Code UI에서 훅 stderr 출력이 별도로 표시됩니다).
4. `docs/ai/workflows/generated/retro-raw.md`를 열어 방금 세션 내용이 실제로
   쌓였는지 최종 확인합니다.

## 부록 — 이 문서에 없는 에러가 나올 때 빠르게 진단하기

위 단계에서 다룬 두 가지(원인 1: `python` 스텁, 원인 2: stdin 인코딩) 말고
**처음 보는 에러**가 나오면, 아래 표에서 에러 메시지의 키워드를 먼저 찾아보세요.
원인을 좁히는 데 보통 몇 초면 충분합니다.

| 에러 메시지에 이 키워드가 있으면 | 십중팔구 원인 | 확인 방법 |
| --- | --- | --- |
| `ModuleNotFoundError`, `ImportError` | 엉뚱한 python 인터프리터를 물고 있음(훅은 표준 라이브러리만 씀, 별도 설치 불필요) | `py -3 -c "import sys; print(sys.executable)"` 로 실제 어떤 python이 실행되는지 확인 |
| `FileNotFoundError`, `[Errno 2]`, `지정된 파일을 찾을 수 없습니다` | 리포지토리 루트가 아닌 다른 폴더에서 실행함 | `git rev-parse --show-toplevel` 로 루트 경로 확인 후 그 경로에서 재시도 |
| `UnicodeDecodeError`, `UnicodeEncodeError` | stdin/파일 인코딩 문제(이번에 고친 버그의 다른 변종일 수 있음) | 훅 파일이 최신인지(`git log -1 .claude/hooks/`), `main()` 상단에 `sys.stdin.reconfigure(encoding="utf-8")`가 있는지 확인 |
| `JSONDecodeError` | 수동 테스트 시 JSON 따옴표/이스케이프를 잘못 입력함 | 2단계 커맨드의 따옴표를 그대로 복붙했는지 확인. 실제 Claude Code 세션 중에는 이 에러가 나도 무시됩니다(모든 훅이 `try/except`로 흡수하도록 작성됨) |
| `PermissionError`, `Access is denied`, `액세스가 거부되었습니다` | 백신/보안 소프트웨어가 스크립트 실행을 차단, 또는 파일이 다른 프로그램에서 열려 잠겨 있음 | 잠시 후 재시도, 반복되면 보안 소프트웨어 예외 목록에 프로젝트 폴더 추가 |
| `SyntaxError` (훅 파일 자체를 가리킴) | 로컬 훅 파일이 손상됐거나 병합 충돌 마커(`<<<<<<<`)가 남아있음 | `git status .claude/hooks/`로 로컬 변경 여부 확인, 변경됐다면 `git checkout -- .claude/hooks/`로 원복 |
| 에러 메시지 없이 그냥 아무 출력도 없음 | `python`이 아직 MS Store 스텁으로 연결됨(원인 1과 동일 계열) | `py -3 --version` 재확인, 안 되면 1단계로 돌아가기 |
| 터미널에서 직접 돌리면 되는데 Claude Code 안에서만 이상함 | 훅이 실행되는 작업 디렉터리가 Claude Code 프로세스 기준이라 다를 수 있음 | Claude Code 세션 안에서 `echo $CLAUDE_PROJECT_DIR` (또는 `$env:CLAUDE_PROJECT_DIR`)를 확인 |

### 진단 정보 한 번에 모으기

표에서도 원인이 안 잡히면, 아래를 한 번에 실행해서 나온 결과를 **그대로 통째로**
팀 채널에 붙여넣어 주세요. 개별 증상만 설명하는 것보다 훨씬 빨리 원인을 찾습니다.

```powershell
py -3 --version
py -3 -c "import sys; print('interpreter:', sys.executable); print('stdin encoding:', sys.stdin.encoding)"
git rev-parse --show-toplevel
git branch --show-current
git log -1 --format="%h %ad %s" -- .claude/hooks/
git status --short .claude/
Get-Content .claude/settings.json
```

## 결과 공유

4단계까지 정상이면 "이상 없음"으로 팀 채널에 짧게 알려주세요. 어느 단계에서든
실패하면, **실패한 단계 번호 + 정확한 에러 메시지(트레이스백 포함)** 를 그대로
공유해주세요 — 조용히 실패하는 게 이번 문제의 원인이었으므로, 에러가 나면 오히려
좋은 신호입니다. 위 표에 없는 처음 보는 에러라면 "진단 정보 한 번에 모으기"
결과까지 같이 붙여서 공유하면 됩니다.
