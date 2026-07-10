# entity-definition.md — 엔티티정의서

> 원본: `WBS_Vibe v5.2 - 엔티티 정의서.csv`. ERD 원본 링크:
> https://www.erdcloud.com/d/8cSuuu7a5qjGTo5t3 (작성 시점 기준 "수정이 더 필요할 수도
> 있다"는 메모가 있었습니다 — 실제 DDL과 어긋나면 이 문서를 최신으로 갱신하세요).
> 다이어그램 스냅샷은 [`erd.png`](erd.png)에 있습니다.
>
> 엔티티 정의 시 **카멜표기법으로 통일**합니다 (컬럼명 예: `googleId`, `extractedText`).

| 엔티티 | 주요 필드 | 도메인 | 관련 요구사항 ID | 비고 |
| --- | --- | --- | --- | --- |
| `User` | `id`, `googleId`, `email`, `name` | ④ 인증 | FR-06, FR-12 | `name` 수정 가능(FR-12) |
| `RefreshToken` | `id`, `userId`, `tokenHash`, `expiresAt`, `revoked` | ④ 인증 | FR-06 | |
| `Resume` | `id`, `userId`, `type`(RESUME/PORTFOLIO), `s3Key`, `extractedText`(마스킹됨), `parseStatus`, `fileHash`, `cacheExpiresAt` | ① 파일 파이프라인 | FR-01, FR-11 | |
| `PersonaConfig` | `id`, `level`(1~3), `tone` | ② 면접 엔진+LLM | FR-03, IV-001, FR-13 | 등급 참고텍스트는 프론트 정적 매핑(FR-13), 백엔드 필드 없음 |
| `InterviewSession` | `id`, `userId`, `resumeId`, `personaConfigId`, `selectedKeyword`, `status` | ② 면접 엔진+LLM | FR-01, FR-02, FR-03 | `resumeId`는 `type=RESUME`만 허용 |
| `Message` | `id`, `sessionId`, `role`, `content`, `turn` | ② 면접 엔진+LLM | FR-03, FR-10, NFR-06 | |
| `AnswerScore` | `id`, `sessionId`, `turn`(1~4), `score`(0~25) | ③ 평가·게이지·해금 | FR-04 | `score`는 5개 세부항목 합산값(내부). 세부점수는 미저장 또는 별도 비공개 컬럼 |
| `JudgmentResult` | `id`, `sessionId`(unique), `totalScore`, `passed`, `overallFeedback` | ③ 평가·게이지·해금 | FR-04, FR-05, FR-08 | 레벨 텍스트는 프론트 정적 매핑 |
| `UserUnlockStatus` | `userId`, `unlockedLevel`, `progressGauge` | ③ 평가·게이지·해금 | FR-05 | `progressGauge`: 레벨클리어당 +30% |
| `Badge` | `id`, `stage`(1~4), `name`, `imageUrl`, `unlockCondition` | ③ 평가·게이지·해금 | FR-09 | 4단계 확정 |
| `UserBadge` | `id`, `userId`, `badgeId`, `acquiredAt` | ③ 평가·게이지·해금 | FR-09 | |

> `PersonaConfig`(`level`, `tone`)와 `InterviewSession.status`의 실제 상태값·전이는
> `docs/state/invariants-and-state-machines.md`에 기획서 15장 원본 기준으로 더 자세히
> 정리되어 있습니다. 두 문서가 어긋나면 함께 갱신하세요.

## 역방향 추적에서 확인할 제약 (`docs/ai/SHARED.md` §3 ① DB 제약)

- `JudgmentResult.sessionId`는 **UNIQUE**여야 합니다 (세션당 최종 판정 1건).
- `Resume`는 사용자당 `type=RESUME` 최소 1개(필수)~최대 3개, `type=PORTFOLIO` 최대 3개(선택)라는
  제약이 있습니다(✅ 2026-07-10 확정, `docs/requirements/open-questions.md` #1). "최대 3개"는
  DB UNIQUE 제약으로 표현할 수 없으므로 애플리케이션 레벨에서 `type`별 개수를 카운트해
  검증해야 하고 그 사실을 코드 주석이 아니라 여기 명시해야 합니다.
- `RefreshToken.revoked`, `expiresAt`을 기준으로 재사용 탐지가 필요합니다
  (`docs/ai/owners/pyo-jimin.md` 체크리스트 참고).
- `Resume.cacheExpiresAt`(업로드 후 30일)이 지난 레코드를 삭제하는 배치가 실제로
  구현되어 있는지 확인하세요 (NFR-14, `docs/ai/owners/lee-geonhui.md` 체크리스트).

## 도메인 ↔ 실제 패키지 매핑

이 표의 "도메인" 컬럼(①~④)은 `docs/ai/README.md`의 "경로 → 오너" 표와 동일한 번호
체계입니다. 실제 코드에서는 아래 패키지에 대응합니다.

| 엔티티 | 실제 패키지 (예상) |
| --- | --- |
| `User`, `RefreshToken` | `domain.auth.entity` |
| `Resume` | `domain.resume.entity` |
| `PersonaConfig` | `domain.persona` |
| `InterviewSession` | `domain.interview.entity` |
| `Message` | `domain.message` |
| `AnswerScore`, `JudgmentResult` | `domain.judgment` |
| `UserUnlockStatus`, `Badge`, `UserBadge` | `domain.progress` |

`domain.persona`, `domain.message`, `domain.judgment`, `domain.progress`는 현재
`package-info.java`만 있는 빈 패키지입니다. 실제 엔티티 클래스를 추가할 때 이 표와
`docs/ai/README.md`가 어긋나지 않는지 확인하세요.
