# ADR-015 — 뱃지 자산을 애플리케이션 정적 리소스로 배포

- 상태: 일부 대체됨(운영 제공은 ADR-021, 로컬 정적 fallback은 유지)
- 작성자: 최용성
- 작성일: 2026-07-20
- 관련 이슈/PR: #52, #101

## 배경

> 2026-07-22: 운영 이미지 제공은 [ADR-021](ADR-021-badge-images-private-s3-presigned-get.md)의
> private S3 계약으로 대체됐다. 이 ADR의 정적 자산 소유·배포 결정은 로컬·테스트 fallback에서 유지된다.

Stage1~4 뱃지 지급과 `BG-001` 조회에는 최종 이름과 실제 이미지 URL이 필요하지만, 기존
문서에는 임시 이름과 `...` URL만 있었다. 제공된 Level1~4 PNG를 프론트·운영 환경에서
같은 경로로 참조하려면 자산 소유 위치와 URL 규칙을 정해야 한다. 별도 CDN/S3 공개 URL은
아직 도메인·버킷·캐시 정책이 확정되지 않았다.

## 결정

- 제공된 1254×1254 PNG 원본을 `src/main/resources/static/badges/Level1.png`~`Level4.png`에
  포함해 백엔드 배포물과 함께 배포한다.
- `Badge.imageUrl`은 API 서버 origin 기준 상대 경로 `/badges/Level1.png`~`/badges/Level4.png`를
  사용한다. 환경별 호스트 이름을 DB 기준 데이터에 저장하지 않는다.
- Flyway `V10__seed_badges.sql`이 `프로그래머쓱 LEVEL 1`~`LEVEL 4`, 이미지 상대 경로와
  Stage별 지급 조건을 함께 초기화한다. 기존 같은 Stage 행은 확정값으로 동기화한다.
- Stage4 자산과 기준 데이터는 함께 배포하지만 MVP 지급 로직은 Stage3까지만 활성화한다.
- 정적 경로를 비인증 공개할지는 `global/security` owner가 후속 이슈 #105에서 결정한다.
  해당 이슈 반영 전에는 기존 Spring Security 정책을 유지한다.
  → **2026-07-20 결정 완료(표지민, 이슈 #105)**: `/badges/**`만 `permitAll()`로 최소
  범위 공개하고, 나머지 `/api/**` 인증 정책은 그대로 유지한다. 뱃지 이미지는 개인정보가
  아닌 고정된 공개 자산 4장이라 별도 인증 fetch+Blob이나 CDN 전환 없이 정적 리소스
  그대로 공개하는 것으로 충분하다고 판단했다. 자세한 근거는
  [ADR-018](ADR-018-badge-image-public-access.md) 참고.

## 핵심 근거

3주 MVP에서 별도 정적 자산 인프라를 추가하지 않고도 DB seed와 실제 파일을 한 배포물에서
검증할 수 있다. 상대 URL을 사용하면 로컬·운영 호스트가 달라도 seed가 동일하며, 향후 CDN을
도입할 때는 새 마이그레이션으로 `imageUrl`만 교체할 수 있다.

## 대안 및 반려

- **프론트 저장소에서 이미지를 소유하고 백엔드는 파일명만 반환** — 백엔드 `imageUrl` 계약과
  프론트 배포 버전이 분리되어 seed와 실제 자산이 어긋날 수 있어 반려했다.
- **S3 또는 CDN 절대 URL을 seed** — 현재 공개 버킷·도메인·캐시 무효화 정책이 없고 환경별
  URL을 DB에 고정해야 하므로 MVP에서는 반려했다.
- **이미지를 Base64/data URL로 DB 저장** — `image_url VARCHAR(255)` 계약에 맞지 않고 응답과
  DB 크기를 불필요하게 키우므로 반려했다.

## 결과 (기대)

- 신규·기존 DB가 같은 Stage1~4 이름과 이미지 경로로 수렴한다.
- `BG-001` 응답의 `imageUrl`이 실제 bootJar에 포함된 PNG와 일치한다.
- 백엔드 bootJar 크기가 원본 이미지 합계만큼 증가한다.
- 향후 CDN 전환이나 비인증 공개 정책 변경은 새 마이그레이션 및 security owner 검토가 필요하며,
  현재 공개 접근 계약은 후속 이슈 #105에서 추적한다.

## 관련 문서

- [`docs/api/api-spec.md`](../api/api-spec.md) — BG-001
- [`docs/requirements/functional-requirements.md`](../requirements/functional-requirements.md) — FR-09
- [`docs/requirements/open-questions.md`](../requirements/open-questions.md) — #10
- [`docs/requirements/planning-overview.md`](../requirements/planning-overview.md) — §5
- [`docs/operations/flyway-migration-guide.md`](../operations/flyway-migration-guide.md) — V10
