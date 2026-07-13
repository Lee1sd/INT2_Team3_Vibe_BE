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
- 이후 로컬은 `ddl-auto: validate`로 전환합니다 — 엔티티와 실제 스키마가 다르면 앱이
  기동 시점에 바로 실패하고, Hibernate가 스키마를 임의로 만들거나 바꾸지 않습니다.

## 2. 마이그레이션 파일 규칙

| 항목 | 규칙 |
| --- | --- |
| 저장 위치 | `src/main/resources/db/migration/` (Flyway 기본 경로, 아직 폴더 없음 — 최초 파일 추가 시 함께 생성) |
| 네이밍 | `V{정수}__{설명}.sql` — `V`와 정수 뒤 **언더스코어 2개**(`__`), 설명은 영어 snake_case 권장 |
| 예시 | `V1__init_schema.sql`, `V2__add_persona_config.sql`, `V3__add_resume.sql` |
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
      ddl-auto: validate   # update → validate. Hibernate가 스키마를 만들거나 바꾸지 않음
  flyway:
    enabled: true
```

빌드 설정에도 Flyway 의존성이 아직 없습니다 (`build.gradle` 확인 결과 `flyway-core`
미포함, 2026-07-13 기준). 첫 `Vn` 파일을 추가하는 PR에서 아래 의존성도 함께 추가해야
합니다.

```groovy
dependencies {
    implementation 'org.flywaydb:flyway-mysql'
}
```

(`spring-boot-starter-data-jpa`를 이미 쓰고 있어 Spring Boot가 `flyway-core` 버전을
자동으로 맞춰줍니다. MySQL을 쓰므로 `flyway-mysql`만 추가하면 됩니다.)

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

## 5. 기존 ERD를 `V1`으로 옮길 때 주의할 점

`docs/erd/entity-definition.md`에는 11개 엔티티가 정의되어 있지만, **2026-07-13 기준
실제 `@Entity` 클래스가 존재하는 건 `domain.auth`(`User`, `RefreshToken`)뿐입니다.**
나머지(`Resume`, `PersonaConfig`, `InterviewSession`, `Message`, `AnswerScore`,
`JudgmentResult`, `UserUnlockStatus`, `Badge`, `UserBadge`)는 담당 패키지가
`package-info.java`만 있는 빈 상태입니다 (설계가 확정된 것과 엔티티 코드가 존재하는 것은
다릅니다 — 예: `PersonaConfig`는 김한비님이 설계를 확정했지만 아직 코드로 존재하지
않습니다).

그래서 **"ERD를 통째로 `V1`에 옮긴다"가 아니라, 지금 코드로 존재하는 엔티티만 `V1`에
담고, 나머지는 각 담당자가 실제로 엔티티를 구현하는 PR에서 자기 몫의 `Vn` 파일을
추가하는 방식**을 제안합니다. 이렇게 해야 "DDL은 있는데 매핑되는 엔티티가 없는" 상태를
피할 수 있습니다.

`V1__init_schema.sql` 예시 (참고용 — 실제 컬럼 타입·제약은 병합 전 최종 확인 필요):

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    google_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_users_google_id UNIQUE (google_id)
);

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

컬럼명은 Spring Boot 기본 네이밍 전략에 따라 엔티티의 카멜케이스 필드(`googleId` 등)가
스네이크케이스 컬럼(`google_id`)으로 매핑된 결과입니다.

이후 각 담당자가 엔티티를 구현하며 추가할 것으로 예상되는 마이그레이션(참고용 — 실제
번호는 병합 순서에 따라 달라질 수 있습니다):

| 예상 버전 | 테이블 | 담당 |
| --- | --- | --- |
| V2 이후 | `resumes` | 이건희 |
| V2 이후 | `persona_configs` | 김한비 (설계 확정, 코드 미구현) |
| V2 이후 | `interview_sessions`, `messages` | 김한비 |
| V2 이후 | `answer_scores`, `judgment_results` | 최용성 |
| V2 이후 | `user_unlock_statuses`, `badges`, `user_badges` | 최용성 |

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