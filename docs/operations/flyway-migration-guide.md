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

## 5. 현재 마이그레이션 현황 (2026-07-23 기준)

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

`V11__cascade_delete_on_user_withdrawal.sql`은 회원 탈퇴 시 사용자 소유 데이터를 DB에서
함께 삭제하도록 관련 FK에 `ON DELETE CASCADE`를 적용합니다.

`V12__seed_persona_config.sql`은 면접 난이도별 페르소나 설정 기준 데이터를 추가합니다.

`V13__add_persona_config_level_unique.sql`은 난이도별 페르소나 설정이 하나만 존재하도록
`persona_config.level`에 UNIQUE 제약을 추가합니다.

`V14__change_selected_keyword_to_varchar.sql`은 `interview_sessions.selected_keyword`를
ENUM에서 `VARCHAR(20)`으로 변경해 애플리케이션 enum 문자열과 DB 스키마의 결합을 낮춥니다.

`V15__add_user_profile_image_key.sql`은 private S3 프로필 이미지 object key를 users에 추가합니다.

`V16__add_resume_soft_delete.sql`은 `resumes.deleted_at` nullable 컬럼과
`(user_id, type, deleted_at)` 인덱스를 추가해 사용자 직접 삭제를 소프트 삭제로 처리합니다.

`V17__AllowNullResumePersonalData.java`는 Resume 삭제 시 개인정보를 파기할 수 있도록
`resumes.s3_key`, `file_hash`의 NOT NULL 제약을 해제합니다. MySQL과 H2의 DDL 문법 차이로
Java 마이그레이션을 사용하며, 예외 근거와 위치 규칙은 §5-1을 따릅니다.

`V18__add_resume_file_cleanup_tasks.sql`은 원본 파일 삭제 작업을 기록하고 재시도하기 위한
`resume_file_cleanup_tasks` 테이블과 생성 시각 인덱스를 추가합니다. 회원 탈퇴 후에도 미처리
파일을 정리할 수 있도록 Resume FK는 두지 않습니다.

`V19__move_badge_images_to_s3_keys.sql`은 `badges.image_url`의 기존 `/badges/**` 상대 경로를
`badges/Level1.png`~`Level4.png` private S3 object key로 전환합니다. 물리 컬럼명은 기존
마이그레이션 호환을 위해 유지하고 Java 엔티티에서는 `imageKey`로 다룹니다(ADR-022).

`V20__AllowNullResumeCleanupTaskResumeId.java`는 회원 탈퇴 후에도 파일 정리 task가
남도록 cleanup task의 `resume_id`를 nullable로 변경합니다.

`V21__add_resume_s3_etag.sql`은 완료 검증·비동기 파싱·조건부 삭제가 동일 객체 버전을
사용하도록 Resume과 cleanup task에 S3 ETag 컬럼을 추가합니다.

`V22__backfill_signup_progress_and_stage1_badges.sql`은 기존 사용자에게 가입 기본 진행도와
Stage1 뱃지를 backfill합니다.

`V24__rebalance_judgment_scoring_constraints.sql`은 이슈 #147의 5문항·문항당 20점 계약을
반영합니다. `answer_scores`의 turn 범위를 1~5, score 범위를 0~20으로 바꾸고
`is_follow_up=true`는 turn 5에만 허용합니다. 레벨별 합격선(Lv.1 60점, Lv.2 80점)은
`judgment_results` 한 행만으로 판단할 수 없으므로 V8의 고정 80점 `passed` CHECK는
제거하고, 세션 레벨을 조회하는 애플리케이션 불변식으로 대체합니다. 총점 0~100과
세션당 단일 최종 판정 제약은 유지합니다. 기존 3+1문항 데이터는 새 4+1문항 계약으로
무손실 변환할 수 없고 현재는 운영 전 개발 데이터가 폐기 가능하므로, 제약 교체 전에
기존 면접 세션을 삭제합니다. V11의 cascade로 메시지·질문·문항점수·최종판정이 함께
제거되며, 해당 사용자의 해금 상태는 Lv.1·게이지 0으로 초기화하고 Stage1을 제외한
획득 뱃지를 제거합니다. 사용자와 이력서는 유지합니다(ADR-023, open-questions #13).

`V26__add_resume_file_metadata.sql`은 이력서 목록 화면에 원본 파일명과 검증된 파일 크기를
표시할 수 있도록 `resumes.original_file_name`, `file_size` nullable 컬럼을 추가합니다.
원래 V23으로 작업했으나, 해당 PR이 오래 열려 있는 동안 V24·V25가 먼저 main에 병합·배포되어
운영 DB에 적용됐습니다. Flyway는 이미 적용된 버전보다 낮은 번호의 새 마이그레이션을
"resolved but not applied"로 거부하므로, 병합 직전 V26으로 재번호했습니다 — 브랜치를
오래 들고 있을 때는 머지 직전에 최신 마이그레이션 번호를 다시 확인해야 합니다.

이후 추가될 마이그레이션 예정 목록 (실제 번호는 병합 순서에 따라 달라질 수 있음):

| 예상 버전 | 내용 | 담당 | 이슈 |
| --- | --- | --- | --- |
| V24 이후 | 추가 스키마 변경 발생 시 | 각 담당자 | — |

### 5-1. Java 마이그레이션 예외: V17

팀 기본 원칙은 `src/main/resources/db/migration/Vn__*.sql`만 사용하는 것입니다. 다만
`resumes.s3_key`와 `file_hash`의 NOT NULL 제약을 해제한 V17은 MySQL의
`ALTER TABLE ... MODIFY ... NULL`과 H2의 `ALTER TABLE ... ALTER COLUMN ... NULL` 문법이
서로 달라 하나의 SQL 파일로 두 환경을 안전하게 지원할 수 없었습니다. Flyway placeholder나
조건부 실행만으로 DB 종류별 DDL 문법을 선택할 수도 없어, DB 제품명을 확인해 해당 SQL만
실행하는 `V17__AllowNullResumePersonalData.java`를 예외적으로 사용했습니다.

Java 마이그레이션 파일은 반드시 `src/main/java/db/migration/` 아래에 두고 파일 안에서
`package db.migration;`을 선언해야 Flyway의 기본 탐색 경로에서 인식됩니다. V17뿐 아니라
예외적으로 추가되는 후속 Java 마이그레이션도 동일한 위치와 패키지 규칙을 따라야 합니다.

이 예외는 범용 허용이 아닙니다. SQL로 동일하게 표현 가능한 후속 변경은 계속 SQL
마이그레이션을 사용하고, Java 마이그레이션을 추가하려면 문법 차이와 대안을 검토한 근거를
이 문서와 PR 본문에 남겨야 합니다.

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
