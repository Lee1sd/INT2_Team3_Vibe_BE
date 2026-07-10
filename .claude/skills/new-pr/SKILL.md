---
name: new-pr
description: >-
  현재 브랜치의 변경사항으로 GitHub PR을 생성한다. 사용자가 "PR 올려줘", "PR 만들어줘",
  "PR 열어줘"처럼 요청하면 이 스킬을 사용해 docs/ai/workflows/templates/pr-body.md를
  실제 변경사항 기준으로 채우고 gh pr create로 PR을 만든다.
allowed-tools:
  - "Bash(git status *)"
  - "Bash(git branch *)"
  - "Bash(git diff *)"
  - "Bash(git log *)"
  - "Bash(gh pr create *)"
  - Read
  - Write
---

## 현재 브랜치/변경 사항 (자동 수집)

- 현재 브랜치: !`git branch --show-current`
- 변경 파일: !`git status --short`
- `main` 대비 diff 통계: !`git diff --stat main...HEAD 2>/dev/null || echo "(main 브랜치 기준 비교 실패 — 로컬 커밋 이력만으로 판단)"`
- `main` 대비 커밋 목록: !`git log main..HEAD --oneline 2>/dev/null`

## 지침

너는 지금 Career Dungeon 백엔드 저장소에서 PR을 대신 작성해 올리는 중이다. 위에서
자동으로 수집한 diff/커밋 정보를 근거로 사용하고, 짐작으로 내용을 채우지 않는다.

### 1. 브랜치 확인

현재 브랜치가 `feat/{이슈번호}-...` 또는 `fix/{이슈번호}-...` 형식(`docs/ai/SHARED.md`
§4-1)인지 확인한다. 아니라면 사용자에게 알리고, 그래도 진행할지 확인받는다. 브랜치명에서
이슈번호를 추출해 "관련 이슈"에 `Closes #{이슈번호}`로 채운다.

### 2. 셀프 리뷰 먼저 수행

`docs/ai/workflows/code-review-culture.md` §3-1 체크리스트로 위에서 수집한 diff를
스스로 검토한다: 디버깅 로그/불필요한 주석이 남아있는지, `docs/ai/SHARED.md` §3
역방향 추적 6문항에 답할 수 있는지, 본인(또는 대화에서 밝혀진 담당자) owner 파일의
"손대지 말 것" 경로를 건드리지 않았는지. **문제가 보이면 PR을 만들기 전에 먼저
사용자에게 알린다** — 조용히 넘어가지 않는다.

### 3. ADR 필요 여부 확인

`docs/adr/how-to-write-adr.md` §3 기준으로 이 변경이 ADR급 결정을 포함하는지
판단한다. 포함한다면 PR 본문에 그 사실을 적고, ADR 파일이 아직 없다면 사용자에게
먼저 작성할지 묻는다.

### 4. 템플릿 로드 및 채우기

`docs/ai/workflows/templates/pr-body.md`를 읽고, 위에서 수집한 실제 diff/커밋
정보를 바탕으로 각 섹션을 채운다. **빈 칸으로 남기지 않는다** — 확실하지 않은 항목은
"확인 필요: ..."로 명시한다. "테스트" 체크리스트는 실제로 확인한 항목만 체크하고,
확인하지 않은 항목은 체크하지 않은 채로 둔다(거짓으로 체크하지 않는다).

### 5. 임시 파일에 저장

채운 본문을 저장소 루트의 `.tmp-pr-body.md`에 `Write` 도구로 저장한다 (`.gitignore`에
이미 포함되어 커밋되지 않는다).

### 6. 사용자에게 미리보기

`gh pr create`를 실행하기 **전에** 채운 본문 전체와 제목을 대화창에 보여주고, 2번에서
발견한 셀프 리뷰 이슈가 있다면 다시 한번 요약해서 보여준 뒤, 이대로 올려도 되는지
확인받는다.

### 7. PR 생성

확인을 받으면 다음 형태로 실행한다:

```bash
gh pr create --title "{제목}" --body-file .tmp-pr-body.md
```

### 8. 마무리

- 성공하면 `.tmp-pr-body.md`를 삭제하고, 생성된 PR URL을 사용자에게 보여준다.
- **최소 1인 리뷰 승인 전까지는 머지하지 않는다**는 규칙(`docs/ai/SHARED.md` §4-3)을
  다시 상기시킨다.
- 실패하면(예: `gh` 미인증, 원격 브랜치 미푸시) 에러를 그대로 보여주고 해결 방법을
  안내한다. 필요하면 먼저 `git push -u origin HEAD`를 실행할지 사용자에게 물어본다.

## 참고 문서

- `docs/ai/workflows/auto-pr.md` — 이슈→브랜치→PR→회고 전체 흐름에서 이 스킬이
  "PR 생성 전 셀프 리뷰" + "PR 생성" 단계를 대체한다.
- `docs/ai/workflows/code-review-culture.md` — 셀프 리뷰 기준, 이모지 룰.
- `docs/adr/how-to-write-adr.md` — ADR 필요 여부 판단 기준.
