# 이건희 진행 상태

이 문서는 `docs/ai/owners/lee-geonhui.md`에서 분리한 진행 상태 기록이다.
owner 파일에는 코드 오너 규칙, 담당 경로, 금지 경로, 참고 문서 목록만 둔다.

## 완료

- [x] `cacheExpiresAt`(업로드 후 30일)이 경과한 `DONE` Resume의 `extractedText`를 비우고
      `EXPIRED`로 전환해 레코드·면접 히스토리는 유지하면서 업로드 슬롯을 반환하는 일일
      배치 구현 및 검증 (NFR-14, [ADR-019](../adr/ADR-019-resume-expiration-preserves-history.md))

## 진행 중

- 현재 없음.

## 미착수

- [ ] 업로드 파일 크기 제한(10MB, `application.yml` multipart 설정)과 API 명세서의 에러
      응답 일치 확인 (NFR-01)
- [ ] type별 개수 제약 — RESUME 최소 1개(필수)~최대 3개, PORTFOLIO 0~최대 3개(선택)의
      서버 강제 확인
- [ ] 파일 파싱 실패를 `parseStatus=FAILED`로 저장하고 명확한 에러 응답으로 처리
- [ ] 원본 파일의 파싱 후 즉시 파기와 try-finally 보장
- [ ] 동일 type 재업로드 시 S3 객체 교체와 DB 갱신이 중복 없이 처리되는지 확인
- [ ] 사용자 ID와 파일 해시 기반 텍스트 캐싱 키 확인 (NFR-03)
- [ ] 이메일 등 PII가 저장 전에 마스킹되고 원본 텍스트가 저장되지 않는지 확인 (FR-11, NFR-13)

