# CLAUDE.md — Career Dungeon Backend 진입점

이 파일은 Claude Code(및 이를 그대로 실행하는 IntelliJ Claude Code 플러그인)가 세션 시작 시
자동으로 읽는 루트 진입점입니다. **규칙 본문은 여기에 두지 않습니다.** 규칙이 여러 곳에 중복되면
문서가 서로 어긋났을 때 어느 쪽이 최신인지 알 수 없기 때문입니다. 이 파일은 "다음에 무엇을 읽어야
하는가"만 안내합니다.

## 하네스가 처음이라면

**"하네스 엔지니어링이 뭔지 하나도 모르겠다"면 다른 어떤 문서보다 먼저
[`docs/ai/harness-engineering-101.md`](docs/ai/harness-engineering-101.md)를 읽으세요.**
쉬운 말로 구조·사용법·자동화·튜닝 건의 방법을 전부 설명합니다.

## 읽는 순서 (SSOT 계층)

1. **`docs/ai/SHARED.md`** — 팀 전체 공통 규칙. `.claude/hooks/session-context.py`가
   `SessionStart` 훅으로 세션 시작 시 이 문서를 자동 주입합니다. 별도로 `@` 하지 않아도 됩니다.
2. **`docs/ai/owners/<본인 이름>.md`** — 담당 도메인의 작업 규칙. **자동 주입되지 않습니다.**
   작업을 시작하기 전에 반드시 직접 `@docs/ai/owners/<본인 이름>.md`로 멘션하세요.
3. **owner 파일의 "추가 필수 참조" 표에 나온 설계 문서** — 필요한 절(section)만 골라서 읽으세요.
   설계 문서 전체를 매번 다 읽지 않습니다.

## 지금 바로 확인할 것

| 하고 싶은 일 | 볼 문서 |
| --- | --- |
| 어떤 코드 경로를 수정할지 → 누구 규칙을 봐야 하는지 확인 | `docs/ai/README.md`의 "경로 → 오너" 표 |
| Claude Code 훅/설정이 실제로 어떻게 동작하는지 확인 | `docs/ai/setup-rules.md` (인터넷 정보는 부정확한 경우가 많습니다) |
| 작업 사고 절차, Git 컨벤션, 역방향 추적 체크리스트 | `docs/ai/SHARED.md` |
| 이슈 → 브랜치 → PR → 회고 흐름 | `docs/ai/workflows/auto-pr.md` |
| 코드 리뷰 문화 (AI/사람 역할, 셀프 리뷰, 이모지 룰) | `docs/ai/workflows/code-review-culture.md` |
| ADR을 왜/언제/어떻게 쓰는지 | `docs/adr/how-to-write-adr.md` |
| 왜 이런 구조로 하네스를 짰는지 근거 | `docs/adr/ADR-002-ai-agent-harness-engineering.md` |

## AI 실험/트러블슈팅 기록

- 서비스 Claude API 실험 기록은 `docs/ai/ai-experiment-log.md`, 하네스 트러블슈팅 기록은
  `docs/ai/harness-troubleshooting.md`를 참고하세요.

## 절대 하지 말 것

## ⚠️ docs/ai/owners/*.md 절대 규칙

**이 파일들은 오직 "코드 오너 규칙"(담당 경로, 금지 경로, 참고 문서 목록)만 담는다. 아래 내용은 절대 여기 넣지 않는다:**

- **진행 상황(완료/진행중/미착수) — 별도 진행 문서에**
- **정책 수치(재요청 횟수 등) — 해당 운영 문서에만**
- **그 외 "지금 상태"를 나타내는 모든 내용**

**이 파일을 수정하기 전에, "이게 오너 규칙(누가 뭘 담당하는지)에 관한 것인가, 아니면 진행 상태/정책 내용인가"를 먼저 자문한다. 후자면 다른 파일에 넣는다.**

- `docs/ai/owners/` 안의 파일을 "AI 면접관 페르소나" 설정으로 착각해서 편집하지 마세요.
  면접관 페르소나(널널한 대리 / 깐깐한 과장 등)는 `domain.persona` 패키지와
  `docs/erd/entity-definition.md`의 `PersonaConfig` 엔티티를 참고하세요.
  `docs/ai/owners/*.md`는 **코드 담당자의 작업 규칙**이며 면접 콘텐츠와는 무관합니다.
- LLM을 직접 호출하는 코드를 새로 작성하기 전에 `docs/ai/SHARED.md`의 "LLM 응답 방어" 항목과
  담당 owner 파일의 추가 필수 참조 문서를 먼저 확인하세요. Mock 모드가 기본값입니다.
