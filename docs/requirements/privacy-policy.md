# privacy-policy.md — 개인정보 처리방침 (내부 설계 기준)

> 원본: `[기획서] 커리어 던전 v5.1.pdf` 14장. 이 문서는 사용자에게 노출할 법적 개인정보
> 처리방침 문안이 아니라, **그 방침을 구현이 실제로 지켜야 하는 설계 제약**입니다.
> FR-11(PII 마스킹), NFR-13(마스킹 정확도)과 함께 읽으세요.

## 수집 항목

- 이력서/포트폴리오 파일(PDF, TXT/MD)
- 구글 계정 정보(OAuth2 인증을 통한 이메일·이름)
- 면접 답변 및 대화 기록
- 마이페이지 프로필 이미지(사용자가 직접 업로드, [ADR-020](../adr/ADR-020-user-profile-image-s3.md))

## 파일 처리 정책

1. 업로드된 원본 파일(PDF/TXT/MD)은 S3 임시 버킷에 저장 후 형식별 추출기로 텍스트를 파싱한다.
2. **파싱 완료 즉시 검증된 원본 파일을 삭제**한다 — 동일 ETag 객체를 다운로드한 뒤에는
   try-finally로 성공/실패 양쪽 케이스 모두 조건부 삭제한다. 다운로드 시 ETag가 이미
   달라졌다면 덮어쓴 새 객체를 파싱하거나 삭제하지 않는다(`docs/operations/failure-policy.md` §1).
3. 파싱된 텍스트만 DB에 저장한다(평문 저장, 암호화 스킵 — 근거는
   `docs/requirements/security-design.md` §5). 이메일 등 PII는 저장 전에 반드시
   마스킹한다(FR-11).
4. **프로필 이미지는 위 1~3과 다른 정책을 따른다.** 이력서 원본과 달리 파싱 대상이
   아니라 서비스에서 계속 보여줘야 하는 데이터이므로, S3에 업로드된 그대로 **계속
   보관**하고 파싱-후-삭제 대상이 아니다([ADR-020](../adr/ADR-020-user-profile-image-s3.md)).
   삭제는 사용자가 직접 교체/제거하거나 회원 탈퇴할 때만 일어난다(아래 "보유 기간 및
   파기" 참고).

## 제3자 제공

파싱된 이력서 텍스트 및 답변 내용은 **채점을 위해 LLM API(외부 서비스)에 전송**된다.
그 외 제3자에게는 제공되지 않는다. 이 문서가 "LLM에 사용자 데이터가 전송된다"는
사실의 SSOT이므로, 새로운 외부 서비스에 사용자 데이터를 보내는 기능을 추가할 때는
이 문서를 먼저 갱신하세요.

## 보유 기간 및 파기

- 대화 기록(질문·답변): 서비스 이용 기간 동안 보존, 개수 제한 없이 축적(한 달 내
  예상 데이터량 기준 문제없다고 판단).
- 회원 탈퇴 시: 전체 즉시 삭제.
- 이력서/포트폴리오 원본: 파싱 완료 즉시 삭제(위 "파일 처리 정책" 참고).
- 추출 텍스트 캐시: 업로드 후 **30일** 경과 시 `extractedText`를 삭제하고
  `parseStatus=EXPIRED`로 전환한다(NFR-14, `cacheExpiresAt`). Resume 레코드와 면접
  히스토리는 서비스 이용 기간 동안 유지한다([ADR-019](../adr/ADR-019-resume-expiration-preserves-history.md)).
  - 사용자가 이력서/포트폴리오를 직접 삭제하면 면접 히스토리 참조를 위한 레코드만 유지하고,
  원본 위치 키·파일 해시·추출 텍스트·원본 파일명·파일 크기 등 파일 메타데이터는 Resume
  레코드에서 즉시 파기(null 처리)한다. 다만 원본 위치 키는 S3 객체 삭제가 완료될 때까지
  `resume_file_cleanup_tasks`에 일시 보관되며, 삭제 성공 시 해당 기록도 함께 제거된다.
  - 직접 삭제 시 원본 파일 정리 작업을 `resume_file_cleanup_tasks`에 먼저 기록하고 배치가
  10분마다 재시도한다. 원본 위치 키는 파일 삭제 성공 시 작업과 함께 즉시 삭제하며, 실패 작업은
  성공할 때까지 보관한다(30일 이내 해결 목표, 30일 초과 시 수동 점검). 회원 탈퇴 CASCADE와
  분리해 탈퇴 후에도 남은 파일을 정리한다. 로그에는 원문 키 대신 비가역적인 식별자만 기록한다.
- 프로필 이미지: 사용자가 새 이미지로 교체하거나(이전 객체 삭제) `DELETE /api/users/me/photo`로
  직접 제거할 때까지 계속 보관. 회원 탈퇴 시 S3 객체 삭제를 시도하되, 삭제가 실패해도
  탈퇴(DB 삭제) 자체는 막지 않는다 — DB에서 `profileImageKey`가 사라지면 접근 경로
  자체가 없어지므로 사실상 접근 불가능해진다([ADR-020](../adr/ADR-020-user-profile-image-s3.md)).

## 구현 시 확인할 것 (역방향 추적 연계)

- [ ] 검증된 동일 ETag 원본 삭제가 try-finally로 보장되고, ETag 불일치 객체는 삭제하지 않는가?
- [x] 이메일 마스킹이 저장 전에 적용되는가? ✅ 정규식 마스킹 후 조건부 UPDATE
      (`ResumePiiMaskingService`, FR-11, NFR-13)
- [x] 회원 탈퇴 API가 실제로 대화 기록/이력서/뱃지 등 관련 레코드를 전부 삭제하는가?
      ✅ 2026-07-18 확인 완료 — `DELETE /api/users/me`(`docs/api/api-spec.md` UP-002),
      DB `ON DELETE CASCADE`로 전체 삭제(ADR-016), `UserWithdrawalCascadeDeleteTest`로 검증
- [x] `cacheExpiresAt <= 현재 시각`인 `DONE` 레코드의 텍스트를 삭제하고 `EXPIRED`로
      전환하는 일일 배치가 존재하는가? ✅ 매일 자정(Asia/Seoul) 실행, 레코드 유지
      (`docs/ai/progress-lee-geonhui.md`, ADR-019)
- [ ] 회원 탈퇴 시 프로필 이미지 S3 객체 삭제를 시도하는가? (`UserService.withdraw()`,
      실패해도 DB 삭제는 진행 — [ADR-020](../adr/ADR-020-user-profile-image-s3.md))
- [ ] 프로필 이미지 교체 시 이전 S3 객체가 삭제되는가? (고아 객체 누적 방지)

## 관련 문서

- `docs/requirements/functional-requirements.md` — FR-11, NFR-13, NFR-14
- `docs/requirements/security-design.md` — 컬럼 암호화 스킵 근거
- `docs/operations/failure-policy.md` — 원본 삭제 실패 처리
