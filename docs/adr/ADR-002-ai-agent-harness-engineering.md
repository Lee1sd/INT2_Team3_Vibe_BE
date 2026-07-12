# ADR-002 — AI 에이전트 하네스 엔지니어링 설계

- 상태: 승인 (2026-07-10)
- 작성자: 하네스 설계 담당 (팀 합의 필요 — 이 문서는 팀 리뷰 전 초안입니다)

## 배경

Career Dungeon 백엔드는 4명 전원이 백엔드를 담당하고, 표지민 1명이 인증·인프라·프론트·
공통코드까지 겸임하는 구조로 3주 실개발(기획 7/8 동결)을 진행합니다. 팀은 사전에
[AIBE5_FinalProject_Team6_BE(FANDROPS)](https://github.com)의 AI 코딩 에이전트 하네스
(`CLAUDE.md`/`SHARED.md`/`personas/*.md`/`.claude/settings.json`/hooks, ADR-004/005/007)를
전부 검토했습니다. FANDROPS는 5명이 도메인별로 완전히 분업한 7주짜리 Gradle 멀티모듈
프로젝트였고, 오버셀·중복결제 같은 동시성 방어가 최우선 과제였습니다. Career Dungeon은
단일 모놀리스, LLM 응답 불안정성과 일정 준수가 최우선 과제라는 점에서 근본적으로 다릅니다.

## 결정

FANDROPS의 **패턴**은 프로젝트 성격과 무관하게 검증된 설계이므로 그대로 채용하고,
**내용**은 Career Dungeon에 맞게 전부 새로 작성합니다.

### 채용한 패턴

1. **SSOT 계층 분리** — `CLAUDE.md`(진입점, 자동 로드) → `docs/ai/SHARED.md`(팀 공통,
   훅으로 자동 주입) → `docs/ai/owners/*.md`(개인, 수동 `@`) → 설계 문서(필요한 절만).
   매 세션 전체 문서를 다 읽지 않아 토큰과 환각을 동시에 줄입니다.
2. **경로 기반 라우팅 표** — "이 코드 경로를 건드릴 땐 누구 규칙을 봐야 하는가"를
   `docs/ai/README.md`의 표로 못박아 애매함을 제거합니다.
3. **역방향 추적 자가검증** — 커밋 전 스스로 답해야 하는 체크리스트. FANDROPS의 Spring
   Boot 공통 4문항을 유지하고, Career Dungeon 특화 리스크(LLM 응답 방어, 비용/시간 가드)
   2문항을 추가했습니다 (`docs/ai/SHARED.md` §3).
4. **Retro 자기진화 루프** — 세션 종료 시 `[Retro]` 한 줄 출력 → 주 단위로 압축.
   FANDROPS는 PR 단위로 파일을 쌓았지만, 3주 프로젝트에는 과하므로 주 단위로 경량화했습니다
   (`docs/ai/SHARED.md` §5).
5. **`setup-rules.md`** — Claude Code 훅/설정에 대해 팀이 실제로 검증한 사실만 기록.
   인터넷 정보가 빠르게 낡기 때문입니다.

### 채용하지 않거나 바꾼 부분

| FANDROPS | Career Dungeon | 이유 |
| --- | --- | --- |
| `personas/*.md` (담당자 파일) | `owners/*.md` | "persona"가 이 프로젝트에서 이미 `domain.persona`(AI 면접관 성향)로 선점됨. 이름 충돌 시 AI도 사람도 혼동 |
| SessionStart 훅 불가 전제, 사람이 매 세션 `@SHARED.md` 수동 입력 (Compliance 01) | `SessionStart` 훅이 `additionalContext`로 SHARED.md 자동 주입 | 공식 문서 확인 결과 `SessionStart` 훅은 실제로 존재하고 컨텍스트 자동 주입이 가능함 (`docs/ai/setup-rules.md` §1). 수동 입력 규칙을 없애 마찰을 줄임 |
| 5개 도메인별 전문화 담당자 | 4개 owner (①이건희 ②김한비 ③최용성 ④+⑤+⑥ 표지민 겸임) | 팀 구성이 다름. 표지민의 겸임 과부하는 "위임 규칙"으로 방어 (`docs/ai/owners/pyo-jimin.md`) |
| Gradle 멀티모듈 하드 룰 | 없음 (단일 모놀리스) | 기획서 6장에서 "모놀리식 + 도메인 중심 개발"로 확정. 멀티모듈 규칙은 3주 프로젝트에 과설계 |
| PR마다 `generated/retro-<PR번호>.md` 축적 | 주 1회 `generated/retro-week{N}.md` 압축 | 3주 프로젝트에서 PR 단위 누적은 오버헤드. 최대 3~4개 파일로 끝나도록 경량화 |

## 오너십 매핑 (WBS 기준)

| 담당자 | 기획서 도메인 | 코드 경로 | owner 파일 |
| --- | --- | --- | --- |
| 이건희 | ① 파일 파이프라인 | `domain/resume/**` | `docs/ai/owners/lee-geonhui.md` |
| 김한비 | ② 면접 엔진 + LLM | `domain/interview/**`, `domain/message/**`, `domain/persona/**`, `resources/prompts/**` | `docs/ai/owners/kim-hanbi.md` |
| 최용성 | ③ 평가·게이지·해금 | `domain/judgment/**`, `domain/progress/**` | `docs/ai/owners/choi-yongseong.md` |
| 표지민 | ④ 인증 + ⑤ 인프라 + ⑥ 프론트 + 공통코드 | `domain/auth/**`, `global/**`, 프론트 리포지토리 | `docs/ai/owners/pyo-jimin.md` |

패키지 구조는 실제 저장소(`src/main/java/com/careerdungeon/domain/*`, `global/*`)를
기준으로 작성했습니다. 기획서의 이상적인 이름(`file/`, `evaluation/`, `common/`)과
실제 코드 패키지명(`resume/`, `judgment/`+`progress/`, `global.common/`)이 다르므로,
이 문서와 `docs/ai/README.md`는 **실제 코드 기준**을 정본으로 삼습니다.

## 자동화 구성

- `.claude/settings.json` — `SessionStart`/`PostToolUse`/`Stop` 3개 이벤트, 4개 훅 등록
  (`PostToolUse`는 `Edit|Write|MultiEdit`, `Bash` 두 matcher 블록으로 나눠져 있음).
- `.claude/hooks/session-context.py` — `SHARED.md`를 세션 시작 시 자동 주입.
- `.claude/hooks/llm-json-guard.py` — `interview`/`judgment`/`progress`/`persona`/
  `prompts` 경로 수정 시 LLM 응답 방어 체크리스트를 다시 상기 (비차단성).
- `.claude/hooks/adr-suggestion.py` — 구조/설정 파일 수정, 결정 키워드, `gh pr/issue
  create` 실행 시 ADR 필요 여부를 리마인드 (비차단성. `docs/adr/how-to-write-adr.md` §5).
- `.claude/hooks/retro-reminder.py` — `feat/*`/`fix/*` 브랜치에서 세션 종료 시, AI가
  이미 `[Retro]`를 출력했으면 `last_assistant_message`에서 파싱해
  `docs/ai/workflows/generated/retro-raw.md`(개인 로컬, `.gitignore`)에 자동 저장하고,
  안 냈으면 형식을 리마인드 (비차단성).
- 네 스크립트 모두 로컬에서 `py`(Windows Python 런처)로 직접 검증했습니다
  (`docs/ai/setup-rules.md` §5의 App 실행 별칭 함정, §3-1의 다중 matcher 검증 포함).
- `.claude/skills/new-issue/SKILL.md`, `.claude/skills/new-pr/SKILL.md` — "이슈/PR
  올려줘"라는 자연어 요청을 감지해 `docs/ai/workflows/templates/*.md`를 채우고
  `gh issue|pr create`까지 실행하는 스킬 2개. 훅과 달리 사람(또는 대화 맥락)의 요청이
  있어야 시작되고, 실행 전 채운 본문을 반드시 미리 보여줍니다. 공식 문서 기준으로
  작성했고 팀 PC 실사용 검증은 아직 안 됐습니다 (`docs/ai/setup-rules.md` §7).

## 결과 (기대)

- 세션당 사람이 직접 입력해야 하는 텍스트가 "owner 파일 1개 `@` 멘션"으로 줄어듭니다.
- LLM 연동 코드를 건드릴 때마다 방어 체크리스트가 자동으로 다시 노출되어, 3주 안에
  스키마 이탈/점수 범위 이탈 같은 결함이 리뷰 전에 걸러질 가능성이 높아집니다.
- 표지민의 겸임 과부하가 "공통 계약 확정 후 위임" 규칙으로 명시적으로 관리됩니다.
- Retro 파일이 최대 3~4개로 끝나 문서 관리 오버헤드가 FANDROPS보다 낮습니다.

## 결정 이력 (Retro 반영 기록)

이 절은 매주 금요일 회고 후, `SHARED.md`/`owners/*.md`를 실제로 고친 근거를 한 줄씩
추가하는 곳입니다. 아직 실제 개발이 시작되지 않았으므로 초기 상태입니다.

- 2026-07-10: 초기 하네스 설계 확정 (이 ADR 작성).
- 2026-07-10: 원본 CSV 6개(`WBS_Vibe v5.1 - *.csv`)와 `Vibe_ERD.png`를
  `docs/requirements/`, `docs/api/`, `docs/erd/`로 옮겨 SSOT를 완성. 이 과정에서
  실제 요구사항명세서(FR-01)와 마이페이지 목업 화면이 이력서/포트폴리오 업로드 개수
  제한(1개 vs 3개)에서 서로 다르다는 것을 발견 — `docs/ai/owners/lee-geonhui.md`에
  "확인이 필요한 SSOT 불일치"로 기록하고 FR-01(원문)을 임시 기준으로 채택.
- 2026-07-10: 팀이 CSV를 v5.2로 갱신 — 요구사항명세서/MVP/작업순서도/api명세서는
  내용 변경 없음, WBS(② 작업 순서 1/2순위 명시, 중요도 조정)와 엔티티정의서(비고
  문구 정정)만 실제 변경. 동시에 Git 컨벤션 4개 규칙(브랜치/커밋 타입/PR 1인승인/
  이슈 담당자)을 `docs/ai/SHARED.md` §4에 확정 반영. 기획서 v5.1 PDF(18페이지, 19장
  전체)를 처음으로 전량 읽고 `docs/requirements/{planning-overview,milestones,
  test-strategy,security-design,privacy-policy}.md`와 `docs/adr/ADR-001,003~006`,
  `docs/state/`, `docs/operations/`를 신규 작성 — 이 과정에서 "3장#4 레벨 해금 단일
  레벨 서술"과 "4장/API IV-001의 2레벨 서술"이 기획서 내부에서도 서로 어긋난다는 것을
  발견해 `docs/requirements/open-questions.md` #5로 기록하고, API 명세서·FR-05·MVP
  기능명세서 3곳이 일치하는 2레벨 해금을 임시 정본으로 채택.
- 2026-07-10: 하네스가 처음인 사람을 위한 ELI5 문서(`docs/ai/harness-engineering-101.md`)를
  추가하고, 코드 리뷰 문화(`docs/ai/workflows/code-review-culture.md` — AI/사람 역할
  분담, 셀프 리뷰, 이모지 룰)와 ADR 작성 가이드(`docs/adr/how-to-write-adr.md` +
  `ADR-TEMPLATE.md`)를 신설. ADR 습관화를 돕기 위해 `.claude/hooks/adr-suggestion.py`
  훅을 추가해 구조/설정 파일 변경, 결정 키워드, `gh pr/issue create` 실행 시점에
  ADR 필요 여부를 리마인드하도록 자동화(비차단성). PR/이슈 템플릿(`.github/*`,
  `docs/ai/workflows/templates/*`)에 셀프 리뷰·ADR 확인 체크리스트를 추가.
- 2026-07-10: "이슈/PR 올려줘 하면 템플릿 참고해서 자동으로 작성되게 할 수 없나"라는
  질문에 답해 `.claude/skills/new-issue/`, `.claude/skills/new-pr/` 스킬 2개를
  신설. 버그 이슈 템플릿(`docs/ai/workflows/templates/issue-bug-body.md`)이
  기능 템플릿만 있고 없었던 것을 발견해 함께 추가. 두 스킬이 쓰는 임시 본문 파일
  (`.tmp-issue-body.md`, `.tmp-pr-body.md`)을 `.gitignore`에 등록.
- 2026-07-10: "`[Retro]`를 채팅창에만 남기고 금요일에 모으면 결국 사람이 여러
  세션의 채팅 기록을 뒤져서 복붙해야 하고, 그렇게 모은 걸 시간순으로 이어붙이면
  뒤죽박죽 아니냐"는 지적을 받아 두 가지를 고침. ① `retro-reminder.py`가
  `last_assistant_message`에서 `[Retro]` 블록을 직접 파싱해 개인 로컬
  `docs/ai/workflows/generated/retro-raw.md`에 자동 저장하도록 변경(수집 자동화,
  `docs/ai/setup-rules.md` §3-2에서 함수·전체 흐름 4케이스 검증). ② 금요일 압축을
  시간순 나열 대신 `docs/ai/workflows/templates/retro-week-body.md` 템플릿으로
  카테고리별 분류·중복 제거하도록 `SHARED.md` §5를 갱신. `retro-raw.md`는 4명이
  각자 브랜치에서 동시에 커밋하면 매 PR마다 충돌하므로 팀 공유 파일로 만들지 않고
  `.gitignore`에 등록 — 대신 금요일에 각자 로컬 파일을 공유하는 절차로 대체
  (`docs/ai/workflows/generated/README.md`).

- 2026-07-13: 주말 자가 머지 예외 조건을 `code-review-culture.md` §5로 신설.
  3주 프로젝트 특성상 주말에 리뷰어 확보가 어려워 개발 흐름이 끊기는 리스크를
  줄이기 위해 "토/일 + Discord 알림 2시간 무응답 + 타 도메인 영향 없음 4항목 +
  셀프 리뷰 기록" 조건 전부 충족 시 자가 머지 허용으로 팀 합의.
  동시에 `retro-reminder.py`의 Windows UTF-8 버그(subprocess `text=True` 시
  cp949 기본값으로 한글 브랜치명 `UnicodeDecodeError`, stderr `UnicodeEncodeError`)를
  수정 — `subprocess.run`에 `encoding="utf-8"` 추가, `main()` 진입 시
  `sys.stderr.reconfigure(encoding="utf-8", errors="replace")` 추가.

## 대안 및 반려

- **FANDROPS `personas/` 이름을 그대로 유지하고 문서 상단에 경고만 추가** — 검토했지만
  이름 자체가 혼동을 유발하는 근본 원인이므로 이름을 바꾸는 쪽을 선택했습니다.
  (`docs/ai/owners/*.md` 상단의 경고 문구는 유지하되, 폴더명은 바꿈)
- **PR 단위 Retro 파일 유지** — 3주 프로젝트에서 파일 수가 관리하기 번거로워질 것으로
  판단해 주 단위 압축으로 대체했습니다.
- **Gradle 멀티모듈 도입** — 기획서에서 이미 모놀리스로 확정했고, 3주 일정에 멀티모듈
  전환 비용을 들일 이유가 없어 반려했습니다.
