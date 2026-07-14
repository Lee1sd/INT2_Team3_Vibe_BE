# ADR-010 — DB 스키마 버전 관리: Flyway 채택 (Hibernate ddl-auto·Liquibase 탈락)

- 상태: 승인
- 작성자: 표지민
- 작성일: 2026-07-13
- 관련 이슈/PR: #30, PR #21

## 배경

팀원이 각자 로컬 MySQL을 쓰고, 이후 운영 서버도 별도로 존재한다. 개발이 진행되면서
테이블이 늘어나고 컬럼이 바뀔 때, 모든 환경에서 스키마가 동일하게 적용됐는지 보장할
방법이 없었다. Hibernate의 `ddl-auto: update`로 개발을 시작했지만 PR #21(Flyway 도입)
리뷰 과정에서 세 가지 문제가 드러났다.

1. `ddl-auto: update`는 컬럼 추가는 하지만 **컬럼 삭제·타입 변경은 하지 않는다** —
   스키마 이력 없이 운영 배포 시 어느 환경에 무엇이 적용됐는지 추적 불가.
2. 누가 스키마를 바꿨는지, 언제 바뀌었는지 **감사 이력(audit trail)이 없다**.
3. Hibernate가 스키마를 바꾸는 것과 Flyway가 스키마를 바꾸는 것이 **동시에 동작하면
   충돌**한다 (ddl-auto: update + Flyway 동시 사용 → 중복 적용 또는 체크섬 오류).

따라서 스키마 버전 관리 도구 하나를 팀 SSOT로 정해야 했다.

## 결정

**Flyway**로 확정한다. DB 스키마 변경은 이제부터 전부 Flyway 마이그레이션 파일로만 관리한다.

- 마이그레이션 파일 위치: `src/main/resources/db/migration/`
- 파일 명명 규칙: `V{순번}__{설명}.sql` (예: `V1__init.sql`, `V2__add_token_hash_unique.sql`)
- `ddl-auto` 설정:
  - 로컬 개발 (`application-local.yml`): `none` — Flyway가 스키마 단독 관리
  - 운영 (`application-prod.yml`): `validate` — 엔티티와 실제 스키마 일치 여부만 검증
- **V1 이후 이미 배포된 마이그레이션 파일은 절대 수정하지 않는다.** 체크섬이 바뀌면
  Flyway가 시작을 거부한다. 스키마 변경은 항상 새 버전(V2, V3…)으로 추가한다.

## 핵심 근거

- **러닝커브 없음**: Flyway는 순수 SQL 파일을 그대로 사용한다. 새 DSL(XML/YAML)을 배울
  필요가 없고, 팀원 모두 SQL에 익숙한 상태다.
- **실행 이력이 DB에 기록됨**: `flyway_schema_history` 테이블에 버전·체크섬·실행 시각이
  자동으로 쌓인다. "이 서버에 V3까지 적용됐는지"를 조회만으로 확인할 수 있다.
- **Spring Boot 자동 설정**: `spring-boot-starter`가 `spring.flyway.*` 프로퍼티를 지원하고,
  마이그레이션 디렉터리만 두면 앱 시작 시 자동 적용된다. 별도 CLI 실행이 필요 없다.
- **체크섬 보호**: 이미 적용한 파일을 실수로 수정하면 Flyway가 시작을 막는다 — 운영
  환경에서 마이그레이션을 임의로 덮어쓰는 사고를 원천 차단한다.

## 대안 및 반려

- **Hibernate `ddl-auto: update` 계속 사용** — 반려. 컬럼 삭제·타입 변경은 자동 적용이
  안 되고, 운영 배포 때 어떤 변경이 실제로 반영됐는지 이력이 남지 않는다. 다른 팀원의
  로컬 DB와 스키마가 조용히 달라져도 알 수 없다.
- **Liquibase** — 반려. Flyway와 기능이 거의 같지만 기본 포맷이 XML/YAML이라 SQL보다
  장황하다. 팀에 Liquibase 경험자가 없고 3주 일정에서 추가 학습 비용이 부담이다.
- **수동 SQL 스크립트 공유(슬랙·노션 등)** — 반려. 어떤 스크립트가 어떤 환경에 적용됐는지
  추적하는 체계가 없고, 적용 순서 오류나 누락이 조용히 발생한다.

## 결과

- PR #21에서 `V1__init.sql`(11개 테이블 전체 초기 스키마)이 합류됐다.
- 팀원 전원이 `application-local.yml`의 `ddl-auto`를 `none`으로 변경해야 한다
  (`application-local.yml.example` 이미 반영).
- 이후 스키마 변경(컬럼 추가, 인덱스 추가 등)은 반드시 새 버전 파일(`V2__…`)로 추가하고
  PR을 통해 리뷰를 받는다.
- 첫 번째 후속 마이그레이션은 PR #36의 `V2__add_user_unlock_status_checks.sql`이다.
  Issue #27의 `refresh_tokens.token_hash` UNIQUE 추가는 이후 버전(V3 이상)을 사용한다.

## 관련 문서

- [PR #21](https://github.com/Lee1sd/INT2_Team3_Vibe_BE/pull/21) — Flyway 도입 및 V1 초기 스키마 (이 ADR의 실제 구현)
- [Issue #27](https://github.com/Lee1sd/INT2_Team3_Vibe_BE/issues/27) — V3 이상: `token_hash UNIQUE` 추가
- [`docs/operations/flyway-migration-guide.md`](../operations/flyway-migration-guide.md) — 마이그레이션 파일 작성·적용 절차 (팀 운영 가이드)
- [`docs/erd/entity-definition.md`](../erd/entity-definition.md) — 엔티티 정의서 (스키마와 항상 동기화 필요)
