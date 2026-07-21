# ADR-020 — 마이페이지 프로필 이미지: 사용자 업로드 + S3 저장 (Presigned GET)

- 상태: 제안
- 작성자: 표지민
- 작성일: 2026-07-21
- 관련 이슈/PR: 이슈 #98, FE #10

## 배경

마이페이지에는 Camera 버튼과 `user.photoURL`을 쓰는 UI가 이미 있지만, 실제로 동작하는
API가 없다. 백엔드 `User` 엔티티에는 `id`/`googleId`/`email`/`name`만 있고 프로필
이미지를 저장할 필드가 없다(이슈 #98). FE #10이 이 결정을 기다리고 있다.

처음엔 "구글 OAuth `picture` 클레임을 그대로 쓰면 되지 않나"라는 선택지도 있었지만,
팀 논의 결과 **사용자가 마이페이지에서 자체적으로 이미지를 업로드**할 수 있어야
한다는 요구사항으로 확정됐다. 이 경우 파일을 어디에 저장하고, 어떻게 서빙할지가
새로 결정해야 하는 문제가 된다.

이 저장소에는 이미 `이력서(Resume)` 업로드가 S3를 쓰는 것으로 설계돼 있지만(이슈 #63),
**아직 실제 S3Client 연동이 구현되지 않았다** — `ResumeService`/`ResumeParsingService`의
`S3Client 연동 시 이 메서드를 실제 PutObject/DeleteObject 호출로 교체한다"는 TODO
주석만 있고, 지금은 임시 로컬 디렉터리 저장으로 동작한다. 즉 이 저장소에는 아직
재사용할 수 있는 S3 클라이언트/설정이 전혀 없다.

또한 이력서 원본 파일은 "임시 업로드 → 파싱 완료 즉시 삭제"(`privacy-policy.md`) 정책인
반면, 프로필 이미지는 **계속 보관되어야 하는 파일**이라 보관 정책 자체가 다르다 —
이력서 파이프라인을 그대로 재사용할 수 없다.

## 결정

1. **업로드 경로**: 브라우저 → `POST /api/users/me/photo`(multipart/form-data, BE
   인증 필요) → BE가 AWS S3에 `PutObject`. 프론트가 S3에 직접 업로드(Presigned PUT)하지
   않는다.
2. **버킷**: `CM-003`(퍼블릭 액세스 전체 차단 + IAM Role 기반 접근 제어)을 그대로
   따르는 **private 버킷**. 이력서용 버킷과 물리적으로 같은 버킷을 써도 되지만,
   객체 키 프리픽스로 용도를 구분한다(`profile-images/{userId}/{uuid}.{ext}`).
3. **DB 저장 값**: `users.profile_image_key` 컬럼에 **S3 object key만** 저장한다.
   URL이나 presigned 값 자체를 저장하지 않는다(만료되는 값이라 저장 의미가 없음).
4. **조회 방식**: `GET /api/users/me`(UP-003) 응답의 `photoUrl` 필드에 **Presigned GET
   URL**(TTL 10분)을 매 요청 새로 생성해서 내려준다. 인증된 프록시 엔드포인트
   (`GET /api/users/me/photo`가 BE를 거쳐 바이트를 스트리밍)는 채택하지 않는다 —
   근거는 "대안 및 반려" 참고.
5. **교체 정책**: 새 이미지 업로드 시 새 객체 키(새 UUID)로 먼저 `PutObject`하고,
   DB의 `profile_image_key`를 새 키로 갱신하는 데 성공한 뒤에야 이전 키를
   `DeleteObject`한다(업로드 성공 → DB 갱신 성공 → 이전 객체 삭제 순서). 같은 키를
   덮어쓰지 않는다 — 업로드 도중 실패해도 기존 사진이 깨지지 않게 하기 위함이다.
6. **삭제 API**: `DELETE /api/users/me/photo`로 프로필 이미지를 제거하고
   `profile_image_key`를 `NULL`로 되돌린다(선택 기능이지만 포함한다 — 업로드만
   있고 제거가 없으면 기능이 불완전하다).
7. **회원 탈퇴 연동**: `UserService.withdraw()`가 `userRepository.delete(user)`를
   호출하기 전에 `user.getProfileImageKey()`가 있으면 S3 `DeleteObject`를 먼저
   시도한다. **S3 삭제가 실패해도 DB 삭제(탈퇴)는 그대로 진행한다** — `privacy-policy.md`의
   "탈퇴 시 전체 즉시 삭제"는 DB 레벨 보장이 최우선이며, S3 삭제 실패로 탈퇴 자체가
   막히면 안 된다. 실패 시 ERROR 레벨로 로그를 남긴다(재시도/정합성 배치는 이번
   스코프 밖 — 아래 "결과" 참고).
8. **로컬 개발**: 실제 AWS S3(개발용 버킷)를 그대로 쓴다. `application-local.yml`에
   버킷/리전만 설정하고, 자격증명은 AWS 기본 자격증명 체인(`~/.aws/credentials` 또는
   환경변수)을 쓴다 — 이미 이 저장소가 Google OAuth 클라이언트 시크릿을
   `application-local.yml`(gitignore 대상)에 실제 값으로 두는 것과 같은 패턴이다.
   LocalStack은 도입하지 않는다 — 근거는 "대안 및 반려" 참고.
9. **테스트**: `S3Client`를 Mockito로 모킹해 업로드/삭제 로직을 단위 테스트한다.
   CI에는 실제 AWS 자격증명이 없으므로 통합 테스트는 S3 호출 부분을 목으로 대체한다
   (기존 LLM Mock 모드, `ClaudeLlmClientHttpFailureTest`의 HTTP 모킹과 같은 패턴).

## 핵심 근거

- Presigned PUT(프론트가 S3에 직접 업로드)이 아니라 BE가 중계하는 이유: 3주 MVP에서
  presigned PUT에 필요한 CORS 설정·서명 발급 API·클라이언트 측 검증(용량/MIME)을
  새로 만드는 것보다, 기존 `RS-001`처럼 이미 검증된 multipart 업로드 패턴을 재사용하는
  쪽이 구현·검증 비용이 훨씬 적다. 프로필 이미지는 대용량 파일이 아니라(2MB 상한)
  BE가 중계해도 성능 부담이 크지 않다.
- Presigned GET을 택한 이유: 인증 프록시 방식은 `<img src="...">` 태그가 `Authorization`
  헤더를 보낼 수 없다는 문제가 있어, 프론트가 이미지를 `fetch`로 받아 Blob URL로
  바꾸는 추가 작업이 필요하다. Presigned GET URL은 일반 `<img>` 태그로 바로 동작하고,
  프로필 사진은 이력서 텍스트만큼 민감한 개인정보가 아니라 짧은 TTL(10분) 동안의
  URL 노출 위험이 감내할 만하다고 판단했다.
- 탈퇴 시 DB 삭제를 S3 삭제보다 우선시킨 이유: `privacy-policy.md`가 보장해야 하는
  것은 "사용자가 탈퇴하면 자신의 데이터에 더 이상 접근할 수 없다"는 것이고, S3
  이미지는 `profile_image_key`가 DB에서 사라지는 순간 `photoUrl`을 만들 방법 자체가
  없어져 사실상 접근 불가능해진다. 남은 S3 객체는 접근 경로가 없는 고아 데이터이며,
  향후 버킷 라이프사이클 정책이나 정기 정합성 배치로 정리할 수 있다.

## 대안 및 반려

- **구글 OAuth `picture` 클레임만 저장** — 자체 업로드 요구사항을 충족하지 못한다.
  반려.
- **공개(퍼블릭) 버킷 + 고정 URL** — `CM-003`("S3 버킷은 퍼블릭 액세스 전체 차단") 위반.
  반려.
- **DB에 이미지 자체를 Base64/BLOB 저장** — 이미지 데이터로 DB 크기가 불필요하게
  커지고, 매 조회마다 큰 컬럼을 읽어야 해 UP-003(로그인 사용자 정보 조회) 응답이
  무거워진다. 반려.
- **이력서 임시 업로드·파싱 후 삭제 파이프라인 재사용** — 이력서는 "파싱 완료 즉시
  원본 삭제"가 정책이지만 프로필 이미지는 "계속 보관"이 요구사항이라 보관 정책이
  정반대다. 같은 파이프라인을 재사용하면 정책이 뒤섞여 나중에 원본 삭제 로직이
  실수로 프로필 이미지에도 적용될 위험이 있다. 반려.
- **프론트가 Presigned PUT으로 S3에 직접 업로드** — MVP 이후 트래픽이 커지면 BE
  중계 비용을 줄이는 합리적인 다음 단계이지만, 지금 당장은 CORS·서명 발급 API·
  클라이언트 검증을 새로 구축해야 해 3주 일정에 맞지 않는다. 반려(후속 검토 대상으로
  남김).
- **인증된 프록시 엔드포인트로 이미지 서빙**(`GET /api/users/me/photo`가 BE를 거쳐
  S3 객체를 스트리밍) — 접근 제어가 요청 단위로 더 엄격하다는 장점은 있지만,
  `<img>` 태그가 `Authorization` 헤더를 보낼 수 없어 프론트가 Blob URL로 변환하는
  추가 작업이 필요하고, 모든 이미지 로드가 BE 대역폭을 거치게 된다. 프로필 사진의
  민감도가 그 정도 접근 통제를 요구하지 않는다고 판단해 반려.
- **LocalStack으로 로컬 S3 에뮬레이션** — 이 저장소에는 Docker Compose나 LocalStack
  선례가 전혀 없고, 4인 팀이 3주 일정에서 새 로컬 인프라를 추가하는 비용이 크다.
  이미 Google OAuth 클라이언트 시크릿을 실제 값으로 로컬에 두는 팀 컨벤션이 있으므로,
  S3도 같은 방식(실제 개발용 버킷 + 자격증명 체인)을 따르는 쪽이 일관적이고 더 쌌다.
  반려.

## 결과 (기대)

- `users.profile_image_key`가 추가되고, `GET /api/users/me` 응답에 `photoUrl`
  (presigned URL, nullable)이 포함된다.
- 마이페이지 Camera 버튼으로 업로드한 사진이 새로고침 후에도 유지된다.
- 탈퇴 시 S3 객체 삭제를 시도하지만, DB 삭제(탈퇴 자체)를 막지는 않는다 — S3 삭제가
  실패하면 고아 객체가 남을 수 있으며, 이를 정리하는 배치/라이프사이클 정책은 이번
  스코프에 포함하지 않고 후속 이슈로 남긴다.
- `domain/resume/**`의 실제 S3 연동(이슈 #63)이 나중에 진행될 때, 이 ADR에서 만든
  `global/config`의 공용 S3Client 빈을 재사용할 수 있다(단, 이력서 도메인 로직 자체는
  이건희가 구현).

## 관련 문서

- `docs/requirements/security-design.md` §2 — S3 버킷 보안 정책(`CM-003`)
- `docs/requirements/privacy-policy.md` — 보유 기간 및 파기 정책
- `docs/erd/entity-definition.md` — `User` 엔티티 `profileImageKey` 필드
- `docs/api/api-spec.md` — UP-003(수정), UP-004/UP-005(신규)
- [ADR-016](ADR-016-user-withdrawal-cascade-delete.md) — 회원 탈퇴 시 DB 전체 삭제와의 정합
- 이슈 #63(이건희) — 이력서 S3 연동. 이 ADR은 이력서 도메인 로직을 건드리지 않는다.
