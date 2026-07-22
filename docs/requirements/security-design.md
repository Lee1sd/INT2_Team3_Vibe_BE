# security-design.md — 보안 설계

> 원본: `[기획서] 커리어 던전 v5.1.pdf` 17장. 인증 방식 자체의 선택 근거는
> `docs/adr/ADR-006-google-oauth2-over-self-auth.md`를 보세요. 이 문서는 **구체적인
> 보안 정책 값**(만료 시간, 검증 방식 등)의 SSOT입니다.

## 1. 인증 보안

| 항목 | 정책 | 이유 |
| --- | --- | --- |
| Access Token | 30분 만료 | 탈취 피해 최소화 |
| Refresh Token | **7일**, `HttpOnly` 항상 적용. `Secure`/`SameSite`는 프로필별(아래 참고) | 보안 저장 |
| 재발급 | 자동 재발급 및 **로테이션** | 보안 강화(재사용 탐지) |
| 로그아웃 | 쿠키 삭제 및 서버 측 무효화 | 토큰 사용 중단 |

- `docs/requirements/functional-requirements.md` FR-06에는 Access Token 만료(30분)만
  명시되어 있고 Refresh Token 만료 기간은 없습니다 — **7일**은 이 문서(기획서 17장)가
  유일한 근거이므로, 코드에서 이 값을 쓸 때는 이 문서를 인용하세요.
- 재발급 로테이션이 실제로 동작하는지는 `docs/ai/owners/pyo-jimin.md` 체크리스트로
  확인합니다.
- **Refresh Token 쿠키의 `Secure`/`SameSite`는 프로필별로 다릅니다** (이전 버전은
  프로필 구분 없이 `Strict`로 기재되어 있었으나, 실제로는 배포 아키텍처상 불가능한
  값이라 이번에 정정합니다):
  - **prod**: `Secure=true`, `SameSite=None`. FE/BE가 서로 다른 도메인(cross-site)으로
    배포되는데, `SameSite=Strict`(또는 `Lax`)는 브라우저가 cross-site 요청에 쿠키를
    아예 실어 보내지 않아 `SameSite=None` 없이는 refresh 자체가 불가능합니다.
    `SameSite=None`은 `Secure=true`와 함께 있어야 브라우저가 쿠키를 받아들입니다.
  - **local**: `Secure=false`, `SameSite=Lax`. FE(`localhost:3000`)/BE(`localhost:8080`)가
    둘 다 HTTP localhost라 `Secure` 쿠키는 저장되지 않습니다.
  - 실제 값은 `RefreshTokenCookieFactory`(`auth.cookie.secure`/`auth.cookie.same-site`
    프로퍼티, 이슈 #117)가 관리하며, `Secure=false`+`SameSite=None` 조합은 기동 시점에
    fail-fast로 막습니다.

## 2. 파일 보안

- 원본 PDF/TXT/MD: S3 임시 버킷 업로드 후 파싱 완료 즉시 삭제(try-finally로 성공/실패 양쪽
  케이스 모두 보장) — `docs/operations/failure-policy.md` §1과 동일 내용.
- S3 버킷: **퍼블릭 액세스 전체 차단** + **IAM Role 기반 접근 제어**(`CM-003` 확정). 이
  정책은 프로필 이미지 버킷에도 동일하게 적용된다.
- 업로드 파일 검증: 확장자 화이트리스트 + 용량 상한 10MB(NFR-01). 비동기 추출 단계에서 PDF는
  `%PDF-` 매직넘버와 PDFBox 파싱, TXT/MD는 엄격한 UTF-8 디코딩과 평문 특성을 검증한다(NFR-01).
- **프로필 이미지(마이페이지, [ADR-020](../adr/ADR-020-user-profile-image-s3.md))**: 이력서
  원본과 달리 파싱 후 즉시 삭제하지 않고 **계속 보관**한다 — 정책이 다르므로 이력서
  파이프라인을 재사용하지 않는다. 허용 MIME `image/jpeg`/`image/png`/`image/webp`,
  최대 2MB. 버킷은 private, DB에는 S3 object key만 저장하고 조회 시 짧은 TTL(10분)의
  Presigned GET URL을 매 요청 새로 발급한다 — URL 자체를 DB나 캐시에 저장하지 않는다.

## 3. API 키 관리

- LLM API 키: 환경변수 관리, 하드코딩 금지, GitHub 커밋 금지.
- 공용 API 키: 1개 통합 관리, 월 예산 상한(hard limit) 설정
  (`docs/operations/llm-cost-policy.md` §2와 동일 내용).

## 4. 데이터 보안

- 추출 텍스트: 평문 DB 저장 — **암호화하지 않는다.** 이유는 아래 §5 "컬럼 암호화
  스킵 근거" 참고. 다만 이메일 등 PII는 FR-11/NFR-13에 따라 **마스킹 후** 저장한다
  (평문 저장과 마스킹은 서로 다른 개념 — 마스킹된 텍스트도 "평문"으로 저장된다는
  뜻이며, 원본 이메일 문자열 자체를 저장하지 않는다).
- 대화 기록(질문·답변): 멀티턴 기능 유지를 위해 서비스 이용 기간 동안 보존, 회원
  탈퇴 시 전체 즉시 삭제.
- 도메인별 접근 제어: 자신의 데이터만 조회 가능, 타 유저 접근 차단(FR-06, 인가).

## 5. 컬럼 암호화 스킵 근거 (기획서 원문, 팀 의사결정으로 인용)

> 원본 파일 삭제만으로 개인정보 유출 리스크의 대부분을 줄일 수 있고, 어차피 AI 호출
> 시점에는 평문으로 복호화해야 하는 구조상 완벽한 보안 효과가 없어 투입 시간 대비
> 효과가 낮다고 판단. 3주 개발 기간 내 우선순위가 더 높은 핵심 기능(LLM 연동, 채점
> 로직 등)에 집중하기 위해 이번 스코프에서는 제외.

이 결정을 뒤집으려면(예: 컬럼 암호화를 추가하려면) 팀 합의 후 이 절을 갱신하고
`docs/adr/`에 별도 ADR을 추가하세요 — 현재는 명시적으로 스코프 아웃된 항목입니다.

## 관련 문서

- `docs/adr/ADR-006-google-oauth2-over-self-auth.md` — 인증 방식 선택 근거
- `docs/requirements/privacy-policy.md` — 개인정보 수집/처리/파기 정책
- `docs/ai/owners/pyo-jimin.md` — 인증 도메인 구현 체크리스트
