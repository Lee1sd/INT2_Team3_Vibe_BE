---
name: new-issue
description: >-
  GitHub 이슈를 생성한다. 사용자가 "이슈 올려줘", "이슈 만들어줘", "이슈 등록해줘",
  "버그 등록해줘", "기능 이슈 열어줘"처럼 요청하면 이 스킬을 사용해
  docs/ai/workflows/templates의 이슈 템플릿을 채우고 gh issue create로 실제 이슈를 만든다.
argument-hint: "[기능/버그에 대한 짧은 설명]"
allowed-tools:
  - "Bash(gh issue list *)"
  - "Bash(gh issue create *)"
  - Read
  - Write
---

## 최근 이슈 (중복 방지용 참고)

!`gh issue list --limit 10 --state open 2>/dev/null || echo "(gh 인증이 안 되어 있거나 조회 실패 — 무시하고 진행)"`

## 지침

너는 지금 Career Dungeon 백엔드 저장소에서 GitHub 이슈를 대신 작성해 올리는 중이다.
아래 순서를 **건너뛰지 말고** 그대로 따른다.

### 1. 이슈 종류 판별

대화 맥락(`$ARGUMENTS` 포함)으로 이 이슈가 **기능(feature)**인지 **버그(bug)**인지
판단한다. 애매하면 넘겨짚지 말고 사용자에게 짧게 물어본다.

### 2. 담당자(assignee) 확보

`docs/ai/SHARED.md` §4-5 규칙에 따라 **작업을 시작하기 전에는 담당자가 지정돼 있어야
한다.** 대화에서 담당자가 이미 언급되지 않았다면 "이 이슈 담당자를 누구로 할까요?
(GitHub 아이디)"라고 물어보고 답을 받은 뒤에 진행한다.

**예외**: 사용자가 담당 미정을 명시했고 당장 착수하지 않는 백로그성 이슈라면, 담당자
없이 만들어도 된다. 이때는 `--assignee`를 빼고, 본문에 우선순위 낮음임을 명시하며,
"착수하는 사람이 자신을 assignee로 지정한 뒤 시작한다"는 문장을 본문에 남긴다.

### 3. 템플릿 로드

- 기능 이슈: `docs/ai/workflows/templates/issue-feature-body.md`를 읽는다.
- 버그 이슈: `docs/ai/workflows/templates/issue-bug-body.md`를 읽는다.

이 두 파일이 SSOT다. 구조를 바꾸지 말고 섹션을 그대로 채운다.

### 4. 내용 채우기

대화 맥락과 코드베이스(필요하면 `Read`/`Grep`으로 관련 파일 확인)를 바탕으로 각
섹션을 채운다. **빈 칸으로 남기지 않는다** — 확실하지 않은 항목은 "확인 필요:
{무엇을 확인해야 하는지}"라고 명시적으로 적는다. 짐작으로 채우지 않는다.

기능 이슈라면 템플릿의 "ADR 필요 여부" 섹션도 `docs/adr/how-to-write-adr.md` §3
기준으로 직접 판단해서 체크한다.

### 5. 임시 파일에 저장

채운 본문을 저장소 루트의 `.tmp-issue-body.md`에 `Write` 도구로 저장한다 (이 파일은
`.gitignore`에 이미 포함되어 있어 커밋되지 않는다).

### 6. 사용자에게 미리보기

`gh issue create`를 실행하기 **전에** 채운 본문 전체와 제목·라벨·담당자를 대화창에
보여주고, 이대로 올려도 되는지 확인받는다. 실제 GitHub에 이슈가 생성되는 것은
되돌리기 번거로운 부수효과이므로, 확인 없이 바로 실행하지 않는다.

### 7. 이슈 생성

확인을 받으면 다음 형태로 실행한다:

```bash
gh issue create \
  --title "[FEAT] {제목}" \
  --body-file .tmp-issue-body.md \
  --label enhancement \
  --assignee {GitHub 아이디}
```

버그라면 `--title "[BUG] {제목}"`, `--label bug`를 사용한다.

2번의 **백로그 예외**에 해당하면 위 명령에서 `--assignee`만 뺀다. 제목 접두사와 라벨은
1번에서 판별한 종류(`[FEAT]`/`enhancement` 또는 `[BUG]`/`bug`)를 그대로 유지한다 —
백로그라는 이유로 종류를 바꾸지 않는다. 대신 본문(`.tmp-issue-body.md`)에 우선순위가
낮다는 것과 착수 시 절차를 반드시 적어둔다. 명령에서 담당자만 빼고 본문에 아무 표시도
남기지 않으면, 나중에 보는 사람이 "빠뜨린 것"인지 "의도한 것"인지 구분하지 못한다.

```bash
# 기능 이슈인 경우. 버그면 --title "[BUG] {제목}", --label bug 를 그대로 쓴다.
gh issue create \
  --title "[FEAT] {제목}" \
  --body-file .tmp-issue-body.md \
  --label enhancement
# --assignee 없음 (백로그성 이슈)
```

본문에 넣을 문장 예시:

```markdown
- 우선순위 낮음 — 당장 착수하지 않는 백로그성 이슈라 담당자를 비워 둔다.
  착수하는 사람이 `gh issue edit {번호} --add-assignee {GitHub 아이디}`로 자신을 지정한 뒤
  시작한다 (`docs/ai/SHARED.md` §4-5).
```

### 8. 마무리

- 성공하면 `.tmp-issue-body.md`를 삭제하고, 생성된 이슈 URL과 번호를 사용자에게 보여준다.
- 실패하면(예: `gh` 미인증) 에러를 그대로 보여주고, 어떻게 해결할지 안내한다. 임시
  파일은 재시도할 수 있으니 성공하기 전까지는 지우지 않는다.
- 이 이슈로 브랜치를 만들 계획이라면 `docs/ai/SHARED.md` §4-1 규칙
  (`feat/{이슈번호}-{기능명}` / `fix/{이슈번호}-{내용}`)을 상기시킨다.

## 참고 문서

- `docs/ai/workflows/auto-pr.md` — 이슈→브랜치→PR→회고 전체 흐름에서 이 스킬이 1번을 대체한다.
- `docs/ai/harness-engineering-101.md` §5 — 이 스킬이 "문서 자동화" 장치 중 하나로 왜 존재하는지.
