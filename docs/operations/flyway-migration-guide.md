# flyway-migration-guide.md — Flyway 마이그레이션 가이드

> 근거: 팀 논의(디스코드, 2026-07-13), 이슈 #23. `ddl-auto: update`로는 각자 로컬 DB
> 스키마가 조금씩 어긋날 위험이 있어, Flyway 마이그레이션 파일 기반으로 스키마를
> 통일하기로 했습니다. 담당: 표지민(인프라). 갱신 시 이 문서와
> `docs/erd/entity-definition.md`가 어긋나지 않는지 함께 확인하세요.

## 1. 왜 마이그레이션 파일로 관리하는가

- 지금까지는 `spring.jpa.hibernate.ddl-auto: update`로 각자 로컬에서 엔티티만 보고
  스키마를 알아서 생성해 왔습니다. 이 방식은 사람마다 로컬 스키마가 미묘하게 달라질 수
  있고, 운영 배포 시에도 "무엇이 실제로 적용된 스키마인지"를 코드(엔티티)만 보고는
  확신할 수 없습니다.
- Flyway 마이그레이션 파일은 스키마 변경 이력을 SQL 파일로 명시적으로 남기고, 모든
  환경(로컬/운영)이 **같은 순서로 같은 파일**을 적용하게 강제합니다.
- 이후 로컬은 `ddl-auto: none`으로 전환합니다 — Flyway가 스키마를 단독으로 관리하고,
  Hibernate는 스키마를 만들거나 바꾸지 않습니다. 운영(`application-prod.yml`)은
  `validate`로 유지해 엔티티-스키마 불일치를 기동 시점에 잡습니다.

## 2. 마이그레이션 파일 규칙

| 항목 | 규칙 |
| --- | --- |
| 저장 위치 | `src/main/resources/db/migration/` (Flyway 기본 경로, PR #21에서 `V1__init.sql`로 폴더 생성 완료) |
| 네이밍 | `V{정수}__{설명}.sql` — `V`와 정수 뒤 **언더스코어 2개**(`__`), 설명은 영어 snake_case 권장 |
| 예시 | `V1__init.sql`(완료), `V3__add_token_hash_unique.sql`, `V4__add_resume.sql` |
| 정수 규칙 | 소수점 버전(`V1.1__...`)은 쓰지 않습니다. 항상 다음 정수를 씁니다 |
| 수정 금지 | **이미 `main`에 머지된 `Vn` 파일은 절대 수정하지 않습니다.** Flyway는 적용된 파일의 체크섬을 기억하고 있어서, 내용을 바꾸면 다음 사람 로컬/운영에서 마이그레이션 자체가 실패합니다.<br>스키마를 더 고쳐야 하면 새 `Vn+1` 파일로 `ALTER`를 추가합니다. |

## 3. 새 마이그레이션 파일을 추가하는 절차

1. **DB 스키마 또는 마이그레이션 대상(테이블·컬럼·인덱스·제약조건·테이블 옵션·뷰·
   프로시저·필수 초기 데이터 등)을 바꾸는 PR에는 반드시 대응하는 `Vn` 파일을 같은 PR에
   포함합니다.** 엔티티 필드 변경뿐 아니라 인덱스 추가처럼 엔티티 코드 변경이 없는
   스키마 변경도 대상입니다. 마이그레이션 파일을 빠뜨리면 `ddl-auto: validate` 전환
   이후에는 앱이 기동조차 되지 않습니다.
2. **버전 번호는 PR을 올리기 직전에 정합니다.** 미리 번호를 예약해두지 말고, PR을 올릴
   때 `git fetch origin main`으로 최신 `main`의 `db/migration/` 폴더를 확인해 "현재 가장
   큰 번호 + 1"을 씁니다. 두 PR이 동시에 같은 번호를 썼다면, 나중에 머지하는 쪽이 번호를
   충돌 없는 다음 정수로 바꿔서 다시 커밋합니다(머지된 파일이 아니라 자기 브랜치의 파일만
   바꾸는 것이므로 §2의 "수정 금지" 규칙과 충돌하지 않습니다).
3. **로컬에서 먼저 검증합니다.** `./gradlew.bat bootRun` 등으로 앱을 띄워 Flyway가 새
   파일을 실제로 적용하는지 확인한 뒤 PR을 올립니다. 오탈자·제약조건 실수는 로컬에서
   먼저 걸러야 리뷰에서 반복 지적되지 않습니다.
4. 리뷰어는 §2 규칙(이미 머지된 파일 수정 여부, 네이밍)과 엔티티-DDL 일치 여부(SHARED.md
   §3 ① DB 제약)를 함께 확인합니다.

## 4. 로컬 세팅 전환 절차

### 4-1. 공통 — `application-local.yml` 설정 변경

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none   # update → none. Flyway가 스키마 단독 관리, Hibernate는 개입 안 함
  flyway:
    enabled: true
```

> Flyway 의존성(`flyway-core`, `flyway-mysql`)과 `build.gradle` 설정은 PR #21에서
> 이미 추가됐습니다. 별도로 추가할 필요 없습니다.

### 4-2. 지금까지 로컬에서 MySQL 연결해서 서버를 실행해본 적이 **없는** 사람

- Docker든 개별 설치든 방식을 팀에서 먼저 정해야 합니다 — **이 문서 작성 시점(2026-07-13)
  기준 아직 미정**입니다. 방식이 정해지면 이 절을 구체적인 명령어로 갱신하세요.
- 방식과 무관하게 공통 절차: MySQL 인스턴스 준비 → `career_dungeon` 스키마(빈 스키마)
  생성 → `application-local.yml`의 `datasource.url/username/password`를 본인 환경에 맞게
  설정 → 앱 최초 기동 시 Flyway가 `Vn` 파일들을 순서대로 적용.

### 4-3. 이미 로컬에 `ddl-auto: update`로 생성된 스키마가 있는 사람

Flyway는 자신이 만들지 않은 기존 테이블을 인식하지 못해 충돌합니다. 기존 스키마를
지우고 Flyway로 처음부터 다시 시작해야 합니다.

> ⚠️ 아래 명령은 `career_dungeon`의 모든 테이블과 데이터를 삭제합니다.
> 앱을 종료하고 `application-local.yml` 및 실제 실행 환경의 datasource URL이
> 로컬 인스턴스인지 확인한 뒤, 필요한 데이터는 먼저 백업하세요. 공유/운영 DB에는
> 절대 실행하지 않습니다.

```sql
DROP DATABASE career_dungeon;
CREATE DATABASE career_dungeon;
```

이후 §4-1 설정을 적용하고 앱을 재기동하면 Flyway가 `Vn` 파일을 처음부터 순서대로
적용합니다.

## 5. 현재 마이그레이션 현황 (2026-07-20 기준)

`V1__init.sql`은 PR #21에서 이미 머지됐습니다. 11개 테이블 전체(users, resumes,
persona_config, messages, interview_sessions, refresh_tokens, judgment_results,
answer_scores, badges, user_badges, user_unlock_status)의 초기 스키마가 포함되어 있습니다.

`V2__add_user_unlock_status_checks.sql`은 PR #36에서 추가됐습니다. `unlocked_level`의
1~4 범위와 `progress_gauge`의 0~100 범위를 DB CHECK 제약으로 강제합니다.

`V3__add_token_hash_unique.sql`은 PR #39에서 추가됐습니다. `refresh_tokens.token_hash`의
중복 저장을 UNIQUE 제약으로 차단합니다.

`V4__add_questions_table.sql`은 이슈 #38에서 추가됐습니다. 질문 메시지별 예상답변을
`questions.message_id` 단일 PK/FK로 저장합니다.

`V5__add_messages_session_role_turn_unique.sql`은 이슈 #42에서 추가됐습니다.
`messages(session_id, role, turn)` 복합 UNIQUE로 같은 턴의 메시지 중복 저장을 차단합니다.

`V6__add_badge_award_constraints.sql`은 이슈 #43에서 추가했습니다. `badges.stage`의 UNIQUE·
1~4 CHECK와 `user_badges(user_id, badge_id)` 복합 UNIQUE로 잘못된 Stage와 중복 지급을 차단합니다.

`V7__add_judgment_persistence_constraints.sql`은 이슈 #81에서 추가했습니다. 최초 확정 평가의
개별 피드백을 `answer_scores.feedback`에 보존하고, `(session_id, turn)` UNIQUE와 turn 1~4,
score 0~25, 꼬리질문 플래그 CHECK를 추가합니다. `judgment_results.total_score`는 0~100
CHECK로 방어하며 기존 `session_id` UNIQUE와 함께 세션당 단일 최종 판정을 강제합니다.

`V8__add_judgment_passed_consistency.sql`은 PR #82 리뷰 보완으로 추가했습니다.
`judgment_results.passed`가 `total_score >= 80`과 항상 일치하도록 CHECK를 추가해
애플리케이션 판정값과 DB 저장값의 불일치를 차단합니다.

`V9__rename_resume_created_at_to_last_uploaded_at.sql`은 `resumes.created_at`을 실제 의미와
일치하는 `last_uploaded_at`으로 변경했습니다.

`V10__seed_badges.sql`은 제공된 Level1~4 자산 계약에 맞춰 Stage1~4의 이름, 이미지 경로,
지급 조건을 기준 데이터로 초기화합니다. 기존 동일 Stage 행이 있으면 확정값으로 동기화하며,
Stage4는 기준 데이터만 준비하고 MVP 지급 로직에서는 사용하지 않습니다.

이후 추가될 마이그레이션 예정 목록 (실제 번호는 병합 순서에 따라 달라질 수 있음):

| 예상 버전 | 내용 | 담당 | 이슈 |
| --- | --- | --- | --- |
| V9 이후 | 추가 스키마 변경 발생 시 | 각 담당자 | — |

## 6. 마이그레이션 대상이 아닌 것

- `src/main/resources/prompts/**`(예: `lenient.txt` 등 페르소나 톤 프롬프트 문구)는 DB
  스키마가 아니라 **파일 기반 리소스**입니다. Flyway 마이그레이션 대상이 아니며, 변경 시
  일반 코드 리뷰(파일 diff)로 충분합니다.

## 7. PR 체크리스트

- [ ] DB 스키마 또는 마이그레이션 대상 변경(엔티티/컬럼뿐 아니라 인덱스·제약조건·뷰·
      프로시저·필수 초기 데이터 포함)에 대응하는 `Vn` 파일을 같은 PR에 포함했는가
- [ ] 이미 `main`에 머지된 `Vn` 파일을 수정하지 않았는가
- [ ] 로컬에서 앱을 기동해 새 마이그레이션이 실제로 적용되는 것을 확인했는가
- [ ] `ddl-auto: validate`가 유지되고 있는가 (실수로 다시 `update`로 바뀌지 않았는가)

## 관련 문서

- `docs/erd/entity-definition.md` — 엔티티 정의서(카멜케이스 필드 기준)
- `docs/ai/owners/pyo-jimin.md` — 표지민 담당 체크리스트
- `docs/ai/SHARED.md` §3 ① — DB 제약 역방향 추적 항목
