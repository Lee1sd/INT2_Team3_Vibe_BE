# ADR-016 — 회원 탈퇴: DB `ON DELETE CASCADE`로 전체 즉시 삭제

- 상태: 제안
- 작성자: 표지민
- 작성일: 2026-07-18
- 관련 이슈/PR: FE #5(INT2_Team3_Vibe_FE), 이슈 #75 후속 논의

## 배경

`docs/requirements/privacy-policy.md`(33, 41번 줄)와 `docs/requirements/security-design.md`
(42번 줄)에 "회원 탈퇴 시 전체 즉시 삭제"가 이미 팀 합의로 명시되어 있었으나, 실제
구현(회원 탈퇴 API)이 없는 상태였다(FE #5가 이 gap을 지적).

`users` 행 하나를 지우려면 이를 참조하는 체인 전체를 지워야 한다:

```
users
 ├─ user_unlock_status
 ├─ resumes ── interview_sessions
 ├─ refresh_tokens
 ├─ user_badges
 └─ interview_sessions
      ├─ messages ── questions
      ├─ answer_scores
      └─ judgment_results
```

이 체인은 auth(표지민) 외에 resume(이건희), interview/message(김한비), judgment(최용성)
4개 도메인의 테이블을 모두 가로지른다. V1 마이그레이션에서 각 FK를 기본값(`RESTRICT`에
해당하는 미지정 상태)으로 만들어뒀기 때문에, 지금 상태로는 `users` 행을 지우면 FK
위반으로 실패한다.

## 결정

`V11__cascade_delete_on_user_withdrawal.sql`에서 위 체인에 속한 FK 10개를
`ON DELETE CASCADE`로 재정의한다. `UserService.withdraw(userId)`는 `userRepository
.delete(user)` 한 줄로 끝나고, 나머지 도메인 데이터 삭제는 DB 엔진이 자동으로 처리한다.
`badges`/`persona_config`처럼 여러 유저가 공유하는 참조(마스터) 테이블로 향하는 FK는
그대로 `RESTRICT`로 남긴다 — 이건 유저 소유 데이터가 아니라서 캐스케이드 대상이 아니다.

`refresh_tokens`는 탈퇴 시 즉시 사라지므로 탈퇴 직후 `/api/auth/refresh`로 새
accessToken을 받을 수 없다. 다만 탈퇴 시점에 이미 발급된 accessToken(30분 수명)은
기존 로그아웃과 동일하게 자연 만료까지 유효할 수 있다 — 새로 도입한 제약이 아니라
기존 정책을 그대로 따른 것이다.

## 핵심 근거

- **auth 도메인이 다른 도메인의 Repository/Entity를 알 필요가 없다.** 애플리케이션
  코드로 5~6개 Repository를 순서대로 호출하는 방식(대안 1)은 도메인 경계를 침범하고,
  순서를 실수하면 FK 위반이 난다. DB 캐스케이드는 이 조율을 스키마 레벨로 옮긴다.
- 이 프로젝트는 마이크로서비스가 아니라 **단일 DB 모놀리식**이라, 도메인이 갈려도
  물리적으로 같은 DB 안에 있다 — DB 캐스케이드가 자연스럽게 맞는 경계다.
- 이력서 원본 파일은 파싱 시점에 이미 즉시 삭제되므로(`ResumeParsingService`), DB 행만
  캐스케이드로 지워도 S3/로컬에 고아 파일이 남지 않는다(코드로 확인 완료).

## 대안 및 반려

- **애플리케이션 코드로 도메인별 Repository를 순서대로 호출** — auth 서비스가 resume/
  interview/message/judgment/badge Repository를 전부 주입받아야 해서 도메인 경계를
  깊이 침범한다. 삭제 순서(자식→부모)를 실수하면 FK 위반이 나고, 테이블이 하나 추가될
  때마다 auth 코드를 계속 고쳐야 한다. 반려.
- **소프트 삭제(탈퇴 플래그만 기록, 실제 삭제는 나중에)** — 실수 복구가 가능하고
  대규모 서비스에서 흔한 방식이지만, 이미 `privacy-policy.md`/`security-design.md`에
  "즉시 삭제"로 팀 합의가 끝나 있어 이 프로젝트 범위에서는 대안으로 검토할 필요가
  없었다(정책이 이미 확정된 사안). 유예기간·배치 파기 로직을 새로 만들 시간도 3주
  일정상 없다.

## 결과 (기대)

- `DELETE /api/users/me` 한 번으로 사용자 소유 데이터 전체(이력서, 면접 세션, 메시지,
  질문/모범답안, 채점 결과, 리프레시 토큰, 뱃지, 진행도)가 삭제된다.
- `badges`/`persona_config` 같은 공유 마스터 데이터는 영향받지 않는다.
- `UserWithdrawalCascadeDeleteTest`가 V10 적용 후 스키마를 재현해 전체 체인 삭제와
  공유 테이블 보존을 함께 검증한다.

## 관련 문서

- `docs/requirements/privacy-policy.md` — "회원 탈퇴 시 전체 즉시 삭제" 원본 근거
- `docs/requirements/security-design.md` — 동일 정책의 보안 설계 관점 서술
- `V11__cascade_delete_on_user_withdrawal.sql` — 실제 FK 재정의
- FE #5(INT2_Team3_Vibe_FE) — 이 결정을 촉발한 프론트 이슈
