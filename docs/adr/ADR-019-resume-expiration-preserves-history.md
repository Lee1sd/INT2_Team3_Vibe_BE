# ADR-019 — Resume 만료 시 레코드를 유지하고 추출 텍스트만 삭제

- 상태: 승인
- 작성자: 이건희
- 작성일: 2026-07-21
- 관련 이슈/PR: 이슈 #108, PR #120

## 배경

원래 NFR-14는 `cacheExpiresAt`이 지난 Resume 레코드 전체를 삭제하도록 요구했다. 그러나
`interview_sessions.resume_id`가 `resumes.id`를 참조하고, 현재 FK가 `ON DELETE CASCADE`로
설정되어 있어 Resume을 삭제하면 연결된 면접 세션과 메시지·질문·채점·판정 결과도 함께
삭제된다.

이는 면접 히스토리를 조회해야 하는 FR-10과 대화 기록을 서비스 이용 기간 동안 보존하도록
정한 `privacy-policy.md`의 정책에 어긋난다. 동시에 만료된 Resume이 사용자별·타입별 최대
3개 업로드 제한을 계속 점유해서는 안 되므로, 히스토리 보존과 업로드 슬롯 반환을 함께
만족하는 수명주기 정책이 필요했다.

## 결정

팀 논의를 통해 **옵션 D를 채택하기로 확정**했다.

업로드 후 30일이 지나 `cacheExpiresAt`이 만료된 `DONE` Resume은 레코드를 삭제하지 않는다.
대신 `extractedText`를 `NULL`로 지우고 `parseStatus`를 `EXPIRED`로 전환한다. `FAILED`와
`EXPIRED` 상태는 업로드 개수 제한 집계에서 제외한다.

이에 따라 `InterviewSession`의 Resume 참조는 유지되고 기존 면접 히스토리도 계속 조회할
수 있다. 만료된 텍스트는 질문 생성에 다시 사용할 수 없으며, 사용자가 새 파일을 업로드하면
반환된 슬롯을 사용할 수 있다.

## 핵심 근거

- 기존 Resume 상태 모델과 만료 배치만 확장하므로 FK 및 Interview 스키마를 바꾸는 대안보다
  구현 범위와 회귀 위험이 작다.
- FR-10의 면접 히스토리 조회와 개인정보 처리방침의 대화 기록 보존 요구를 지키면서,
  수명이 끝난 추출 텍스트는 실제로 파기한다.
- `EXPIRED`를 업로드 제한 집계에서 제외해 히스토리 보존 때문에 새 이력서/포트폴리오
  업로드가 막히는 문제도 함께 해결한다.

## 대안 및 반려

- **옵션 A: Resume 레코드 전체 삭제** — 원래 계획이다. 현재 FK의 `ON DELETE CASCADE`로
  면접 세션과 전체 히스토리까지 삭제되어 FR-10 및 대화 기록 보존 정책과 충돌하므로 반려했다.
- **옵션 B: `interview_sessions.resume_id` FK를 `SET NULL`로 변경** — Resume 없이도
  InterviewSession이 유효하도록 nullable 제약, 엔티티 매핑, 조회 로직과 API 계약을 함께
  바꿔야 하므로 스키마 변경 범위가 크다. 이번 TTL 구현 범위를 넘어 반려했다.
- **옵션 C: InterviewSession에 Resume 정보 스냅샷 저장** — 삭제 후에도 면접 당시 정보를
  독립적으로 보존할 수 있지만, 스냅샷 필드·생성 시점·PII 및 별도 만료 정책까지 새로
  설계해야 해 변경 범위가 크므로 반려했다.
- **옵션 D: `EXPIRED` 상태 도입, 레코드는 유지하고 텍스트만 삭제** — 기존 참조 구조를
  유지하면서 히스토리 보존, 텍스트 파기, 슬롯 반환을 가장 작은 범위로 충족하므로 채택했다.

## 결과 (기대)

- resume 도메인은 매일 자정 만료 배치에서 대상 Resume의 `extractedText`를 `NULL`로 만들고
  `parseStatus=EXPIRED`로 전환한다.
- interview 도메인은 기존 `resume_id` 참조와 면접 히스토리를 그대로 보존한다.
- 만료 Resume은 조회할 수 있지만 추출 텍스트는 반환하지 않으며 새 면접 생성에 사용할 수 없다.
- `FAILED` 및 `EXPIRED` Resume은 최대 3개 업로드 제한에서 제외되어 새 업로드 슬롯을 반환한다.
- 회원 탈퇴 시 전체 즉시 삭제하는 ADR-016의 정책은 그대로 유지된다. 이 결정은 TTL 만료에만
  적용된다.

## 관련 문서

- [`functional-requirements.md`](../requirements/functional-requirements.md) — FR-10, NFR-14
- [`privacy-policy.md`](../requirements/privacy-policy.md) — 대화 기록 및 추출 텍스트 보존 기간
- [`entity-definition.md`](../erd/entity-definition.md) — Resume/InterviewSession 관계
- [`invariants-and-state-machines.md`](../state/invariants-and-state-machines.md) — `Resume.parseStatus` 전이
- [`ADR-016`](ADR-016-user-withdrawal-cascade-delete.md) — 회원 탈퇴 시 전체 즉시 삭제 정책

