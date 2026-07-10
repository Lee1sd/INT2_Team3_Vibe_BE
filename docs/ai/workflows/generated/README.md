# generated/ — 주간 회고 압축본

이 폴더에는 `docs/ai/SHARED.md` §5에 정의된 매주 금요일 회고 압축 파일만 둡니다.

- 파일명: `retro-week{N}.md` (예: `retro-week1.md`)
- 내용: `docs/ai/workflows/templates/retro-week-body.md` 템플릿에 맞춰 **카테고리별로
  분류·정리한** 그 주의 회고 (룰 작동 / 튜닝 제안 / SSOT 동기화 필요 / Edge Case)
- 이 파일들은 커밋 대상입니다. 3주 프로젝트 기준 최대 3~4개 파일로 끝납니다.
- 개선사항은 다음 주 시작 전 `docs/ai/SHARED.md` / `docs/ai/owners/*.md`에 반영하고,
  반영 근거를 `docs/adr/ADR-002-ai-agent-harness-engineering.md`의 "결정 이력" 절에
  한 줄로 추가하세요.

## `retro-raw.md`는 이 폴더에 있지만 커밋 대상이 아닙니다

`retro-raw.md`는 `.claude/hooks/retro-reminder.py`가 `feat/*`/`fix/*` 브랜치에서
세션이 끝날 때마다 그 사람의 `[Retro]` 출력을 자동으로 쌓아두는 **개인 로컬 원본**
입니다. `.gitignore`에 등록되어 있습니다 — 4명이 각자 브랜치에서 동시에 같은
파일에 쓰면 PR마다 병합 충돌이 나기 때문에, 팀 공유 파일로 만들지 않고 각자의
로컬에만 둡니다.

**금요일 회고 절차**:

1. 각자 자기 로컬의 `retro-raw.md` 내용을 팀 채널(또는 화면 공유)로 공유합니다.
2. 한 명이 `docs/ai/workflows/templates/retro-week-body.md`를 복사해, 4명 분량을
   카테고리별로 분류·중복 제거·요약해서 채웁니다.
3. 채운 파일을 `retro-week{N}.md`로 저장하고 커밋합니다.
4. 각자 자기 로컬 `retro-raw.md`는 이번 주 내용이 `retro-week{N}.md`에 반영됐으면
   지우거나 비워서 다음 주를 새로 시작합니다 (지우지 않아도 다음 주 내용과 함께
   누적되지만, 다음 압축 때 헷갈리지 않으려면 비우는 걸 권장합니다).

이슈/PR 본문 작성 중간 산물(`.tmp-issue-body.md`, `.tmp-pr-body.md`)은 이 폴더에
두지 않습니다.
