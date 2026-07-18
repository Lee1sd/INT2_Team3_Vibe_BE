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
| `Resume` | `id`, `userId`, `type`(RESUME/PORTFOLIO), `s3Key`, `extractedText`(마스킹됨), `parseStatus`, `fileHash`, `cacheExpiresAt`, `lastUploadedAt` | ① 파일 파이프라인 | FR-01, FR-11 | `lastUploadedAt`은 최근 업로드 시각을 의미하며 재업로드 시 현재 시각으로 갱신됨 |
| `PersonaConfig` | `id`, `level`(1~3), `tone` | ② 면접 엔진+LLM | FR-03, IV-001, FR-13 | 등급 참고텍스트는 프론트 정적 매핑(FR-13), 백엔드 필드 없음 |
| `InterviewSession` | `id`, `userId`, `resumeId`, `personaConfigId`, `selectedKeyword`, `status` | ② 면접 엔진+LLM | FR-01, FR-02, FR-03 | `resumeId`는 `type=RESUME`만 허용 |
| `Question` | `messageId`(단일 PK/FK→`Message.id`), `expectedAnswer` | ② 면접 엔진+LLM | FR-03, FR-04 | `messageId` 단일 PK/FK(2026-07-14, 김한비 판단으로 `{sessionId, questionId}` 복합 UNIQUE에서 번복 — `docs/requirements/open-questions.md` #9 확정 기준, 이슈 #26 코멘트 참고). `questionText`는 별도 저장하지 않는다(질문 메시지는 이미 `Message.content`에 있음). 질문생성 LLM과 채점 LLM이 분리된 구조라 질문 생성(FR-03) 시 생성된 모범답안을 저장해 뒀다가 채점(FR-04) 호출에서 해당 질문 `Message.id`로 조회해 재사용한다(최용성 확인 완료). `expectedAnswer`는 API 응답·화면에 노출 안 함(채점 로직 내부 전용). MVP 채점 정확도 목적이며 스트레치골(FEAT-15 데이터 플라이휠)과는 무관 |
| `Message` | `id`, `sessionId`, `role`, `content`, `turn` | ② 면접 엔진+LLM | FR-03, FR-10, NFR-06 | `(sessionId, role, turn)`은 세션 안에서 유일해야 한다. 질문 생성 재시도/중복 호출 시 같은 turn의 QUESTION이 중복 저장되면 `Question.messageId` 기반 expectedAnswer 조회가 API의 `questionId`/turn 의미와 어긋나 모호해지므로 DB UNIQUE 제약으로 강제한다. |
| `AnswerScore` | `id`, `sessionId`, `turn`(1~4), `score`(0~25), `isFollowUp`, `feedback` | ③ 평가·게이지·해금 | FR-04 | 최초 turn 1~3 서버 확정 점수·피드백을 보존하고 최종 turn 4 채점 시 재사용한다. `score`는 5개 세부항목 합산값(내부), `(sessionId, turn)`은 UNIQUE, `isFollowUp=true`는 turn 4만 허용 |
| `JudgmentResult` | `id`, `sessionId`(unique), `totalScore`, `passed`, `overallFeedback` | ③ 평가·게이지·해금 | FR-04, FR-05, FR-08 | `totalScore`는 0~100, `passed`는 반드시 `totalScore >= 80`에서 파생하고 DB CHECK로 일치성을 강제한다. 레벨 텍스트는 프론트 정적 매핑 |
| `UserUnlockStatus` | `userId`, `unlockedLevel`, `progressGauge` | ③ 평가·게이지·해금 | FR-05 | `progressGauge`: Stage1/2/3 클리어 시 누적 30/60/100% |
| `Badge` | `id`, `stage`(1~4, unique), `name`, `imageUrl`, `unlockCondition` | ③ 평가·게이지·해금 | FR-09 | 4단계 확정. `stage`는 UNIQUE·CHECK(1~4) |
| `UserBadge` | `id`, `userId`, `badgeId`, `acquiredAt` | ③ 평가·게이지·해금 | FR-09 | `{userId, badgeId}` 복합 UNIQUE로 중복 지급 방지 |

> `PersonaConfig`(`level`, `tone`)와 `InterviewSession.status`의 실제 상태값·전이는
> `docs/state/invariants-and-state-machines.md`에 기획서 15장 원본 기준으로 더 자세히
> 정리되어 있습니다. 두 문서가 어긋나면 함께 갱신하세요.

## 역방향 추적에서 확인할 제약 (`docs/ai/SHARED.md` §3 ① DB 제약)

- `JudgmentResult.sessionId`는 **UNIQUE**여야 합니다 (세션당 최종 판정 1건).
- `JudgmentResult.totalScore`는 0~100이고 `passed = (totalScore >= 80)` 불변식을
  DB와 애플리케이션 양쪽에서 강제합니다.
- `AnswerScore`는 `(sessionId, turn)` 복합 **UNIQUE**여야 하며 `turn` 1~4, `score` 0~25를
  DB와 애플리케이션 양쪽에서 강제합니다. `feedback`은 ADR-014의 최종 종합 피드백용
  읽기 전용 컨텍스트를 다음 요청에서 복원해야 하므로 NOT NULL로 보존합니다.
- `UserUnlockStatus.userId`는 **PK이자 `User.id` FK**여야 합니다 (사용자당 진행도 1건).
  `unlockedLevel`은 1~4, `progressGauge`는 0~100 범위를 DB와 애플리케이션 양쪽에서 강제합니다.
- `Badge.stage`는 **UNIQUE**이고 1~4 범위를 벗어날 수 없어야 합니다. `UserBadge`는
  `{userId, badgeId}` 복합 **UNIQUE**로 동일 뱃지의 중복 지급을 DB에서도 차단합니다.
- `Resume`는 사용자당 `type=RESUME` 최소 1개(필수)~최대 3개, `type=PORTFOLIO` 최대 3개(선택)라는
  제약이 있습니다(✅ 2026-07-10 확정, `docs/requirements/open-questions.md` #1). "최대 3개"는
  DB UNIQUE 제약으로 표현할 수 없으므로 애플리케이션 레벨에서 `type`별 개수를 카운트해
  검증해야 하고 그 사실을 코드 주석이 아니라 여기 명시해야 합니다.
- `RefreshToken.revoked`, `expiresAt`을 기준으로 재사용 탐지가 필요합니다
  (`docs/ai/owners/pyo-jimin.md` 체크리스트 참고).
- `Resume.cacheExpiresAt`(업로드 후 30일)이 지난 레코드를 삭제하는 배치가 실제로
  구현되어 있는지 확인하세요 (NFR-14, `docs/ai/owners/lee-geonhui.md` 체크리스트).
- `Question.messageId`는 `Message.id`를 참조하는 **단일 PK/FK**입니다 (2026-07-14 번복,
  `docs/requirements/open-questions.md` #9). 이전 `{sessionId, questionId}` 복합 UNIQUE
  설계는 폐기되었습니다 — `questionId`별로 별도 UNIQUE 제약을 걸 필요가 없습니다.
- `Message`는 `(sessionId, role, turn)` **UNIQUE** 제약을 가져야 합니다. `Question`이
  `messageId` 단일 PK/FK를 쓰더라도, API와 채점 흐름은 세션 안의 질문 turn을 하나의 질문 식별자로
  취급하므로 같은 세션·역할·turn 메시지가 중복되면 expectedAnswer 조회가 모호해집니다.

## 도메인 ↔ 실제 패키지 매핑

이 표의 "도메인" 컬럼(①~④)은 `docs/ai/README.md`의 "경로 → 오너" 표와 동일한 번호
체계입니다. 실제 코드에서는 아래 패키지에 대응합니다.

| 엔티티 | 실제 패키지 (예상) |
| --- | --- |
| `User`, `RefreshToken` | `domain.auth.entity` |
| `Resume` | `domain.resume.entity` |
| `PersonaConfig` | `domain.persona` |
| `InterviewSession` | `domain.interview.entity` |
| `Question` | `domain.interview.entity` |
| `Message` | `domain.message` |
| `AnswerScore`, `JudgmentResult` | `domain.judgment` |
| `UserUnlockStatus`, `Badge`, `UserBadge` | `domain.progress` |

위 표는 현재 실제 엔티티 패키지와 일치합니다. 새 엔티티를 추가하거나 패키지를 이동할 때
이 표와 `docs/ai/README.md`의 owner 경로가 어긋나지 않는지 함께 확인하세요.
