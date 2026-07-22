# ADR-021 — 뱃지 이미지를 private S3와 Presigned GET으로 제공

- 상태: 제안
- 작성자: 최용성
- 작성일: 2026-07-22
- 관련 이슈/PR: #132, FE #42

## 배경

ADR-015와 ADR-018은 Stage1~4 PNG를 백엔드 정적 리소스로 포함하고 `/badges/**`를
공개하는 방식을 채택했다. 이후 네 이미지를 팀 S3 버킷에 업로드해 백엔드와 프론트가
동일한 자산을 사용하라는 요구가 추가됐다. 기존 CM-003은 S3 퍼블릭 액세스 전체 차단과
IAM Role 기반 접근을 확정했으므로, 단순 공개 ACL이나 공개 버킷 URL은 사용할 수 없다.

## 결정

- Level1~4 PNG를 private 버킷
  `int-team3-286688739992-ap-northeast-2-an`의 `badges/Level1.png`~`Level4.png`에 저장한다.
- `badges.image_url` 물리 컬럼에는 환경별 호스트나 만료 서명이 포함된 URL이 아니라
  `badges/LevelN.png` object key를 저장한다. Java 엔티티에서는 의미를 분명히 하기 위해
  `Badge.imageKey`로 다룬다.
- 운영 환경의 인증된 `BG-001` 조회 시 각 보유 뱃지의 object key로 10분 TTL Presigned GET
  URL을 생성해 기존 `imageUrl` 응답 필드에 반환한다.
- 로컬·테스트 환경은 AWS 자격증명을 요구하지 않고 같은 object key를 백엔드 정적 경로
  `/badges/LevelN.png`로 변환한다. `badge.images.use-s3`의 기본값은 `false`이고 `prod`
  프로필에서만 `true`로 덮어쓴다.
- S3 버킷은 계속 private으로 유지하며, 업로드·서명 권한은 EC2 IAM Role의 기본
  자격증명 체인을 사용한다. 공개 ACL을 추가하지 않는다.
- 기존 가입·Lv.1·Lv.2 클리어 지급 트리거, Stage4 MVP 지급 비활성, UserBadge 중복 방지
  불변식은 변경하지 않는다.
- 프론트는 절대 `imageUrl`을 직접 `<img>`에 사용한다. 개발용 상대 경로는
  `VITE_API_BASE_URL` origin을 붙이고, URL 누락·만료·네트워크 실패 시 기존 아이콘을
  fallback으로 표시한다.

## 핵심 근거

private S3 + Presigned GET은 기존 CM-003과 이미 구현된 공용 `S3Presigner` 인프라를
그대로 재사용한다. API 필드명과 지급 도메인 계약을 유지하면서 저장 위치만 분리할 수 있고,
프론트는 Authorization 헤더가 없는 일반 `<img>` 요청으로도 이미지를 읽을 수 있다.

## 대안 및 반려

- **S3 객체 또는 버킷을 공개하고 고정 URL 반환** — 구현은 단순하지만 퍼블릭 액세스 전체
  차단을 요구하는 CM-003을 위반하므로 반려했다.
- **CloudFront + Origin Access Control** — 안정적인 공개 URL과 캐시 제어에 유리하지만,
  현재 배포 범위에 배포판·도메인·인증서 구성이 없고 인프라 변경이 커서 후속 과제로 남긴다.
- **기존 애플리케이션 정적 리소스 유지** — S3 단일 자산 제공이라는 새 요구사항을 충족하지
  못해 ADR-015·ADR-018의 주 제공 방식은 이 ADR로 대체한다.

## 결과 (기대)

- 백엔드 bootJar와 이미지 트래픽의 배포 수명주기를 분리하고 네 Stage가 같은 S3 prefix를
  사용한다.
- `BG-001`을 다시 호출하면 만료된 URL 대신 새 10분 URL을 받을 수 있다.
- 로컬 실행은 S3 버킷·AWS Access Key 없이 백엔드에 포함된 동일한 Level1~4 PNG를 표시한다.
- S3 URL 생성 실패가 뱃지 지급 트랜잭션을 롤백시키지는 않는다. 지급은 쓰기 시점에 끝나고,
  URL 생성은 별도의 읽기 API에서 수행한다.
- ADR-015·ADR-018은 과거 결정 기록으로 보존하되 현재 제공 계약은 이 ADR을 따른다.

## 관련 문서

- [`docs/api/api-spec.md`](../api/api-spec.md) — BG-001, CM-003
- [`docs/requirements/functional-requirements.md`](../requirements/functional-requirements.md) — FR-09
- [`docs/erd/entity-definition.md`](../erd/entity-definition.md) — Badge
- [`docs/state/invariants-and-state-machines.md`](../state/invariants-and-state-machines.md) — §5
- [ADR-015](ADR-015-badge-assets-served-by-application.md)
- [ADR-018](ADR-018-badge-image-public-access.md)
- [ADR-020](ADR-020-user-profile-image-s3.md)
