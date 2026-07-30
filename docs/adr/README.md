# ADR — Architecture Decision Records

> ADR을 왜 습관화해야 하는지, 어떤 변경이 ADR급인지, 실제로 어떻게 쓰는지는
> [`how-to-write-adr.md`](how-to-write-adr.md)를 보세요. 새 ADR을 쓸 때는
> [`ADR-TEMPLATE.md`](ADR-TEMPLATE.md)를 복사해서 시작하세요.

번호는 작성 순서대로 부여합니다. 반려된 결정도 지우지 않고 상태를 "반려"로 남깁니다.
팀 ADR을 우선하고, 개인 담당 도메인 안에서만 영향을 주는 결정은 제목 앞에 `[개인]`을
붙여 이어서 번호를 부여합니다(`how-to-write-adr.md` §2).

| 번호 | 제목 | 상태 |
| --- | --- | --- |
| [ADR-001](ADR-001-monolith-domain-centric.md) | 모놀리식 + 도메인 중심 아키텍처 채택 근거 | ✅ 작성 완료 (기획서 v5.1 16장 근거) |
| [ADR-002](ADR-002-ai-agent-harness-engineering.md) | AI 에이전트 하네스 엔지니어링 설계 | ✅ 작성 완료 |
| [ADR-003](ADR-003-llm-vendor-selection.md) | LLM 벤더 선정 (Claude 확정, Gemini/GPT 탈락) | ✅ 작성 완료. 세부 모델 확정은 [ADR-007](ADR-007-llm-model-selection-haiku45.md) 참고 |
| [ADR-004](ADR-004-polling-over-sse.md) | 진행 상태 조회 방식 (폴링 vs SSE, SSE 탈락) | ✅ 작성 완료 |
| [ADR-005](ADR-005-context-injection-direct-vs-rag.md) | 이력서 컨텍스트 주입 방식 (직접 주입 vs RAG, RAG 탈락) | ✅ 작성 완료 |
| [ADR-006](ADR-006-google-oauth2-over-self-auth.md) | 인증 방식 (Google OAuth2 vs 자체 로그인, 자체 로그인 탈락) | ✅ 작성 완료 |
| [ADR-007](ADR-007-llm-model-selection-haiku45.md) | LLM 세부 모델 확정 — Claude Haiku 4.5 | ✅ 작성 완료 |
| [ADR-008](ADR-008-evaluation-response-dto-split.md) | 채점 응답 DTO 분리 (ADR-014에서 최종 범위를 turn 4 단독으로 변경) | ✅ 작성 완료 |
| [ADR-009](ADR-009-judgment-evaluation-port.md) | judgment 단계별 채점 포트와 Mock 평가 구현의 경계 | 제안 (이슈 #5 교차-owner 리뷰 필요) |
| [ADR-010](ADR-010-flyway-schema-migration.md) | DB 스키마 버전 관리: Flyway 채택 (Hibernate ddl-auto·Liquibase 탈락) | ✅ 작성 완료 |
| [ADR-011](ADR-011-question-evaluation-rubric-fields.md) | global.llm 최종 채점 계약 4문항 정합화 + QuestionEvaluation 루브릭 필드 추가 | 일부 대체 (문항 범위·배점은 ADR-023, 루브릭 필드는 유지) |
| [ADR-012](ADR-012-refresh-token-httponly-cookie.md) | Refresh Token 저장 전략: HttpOnly 쿠키 + Access Token 분리 (로컬 프로필 값은 이슈 #117에서 추가) | ✅ 승인 (PR #37, #119) |
| [ADR-013](ADR-013-question-generation-single-call.md) | 질문 생성 시 질문과 모범답안을 단일 LLM 호출로 함께 생성 | 일부 대체 (질문 수는 ADR-023, 단일 호출은 유지) |
| [ADR-014](ADR-014-follow-up-only-final-evaluation.md) | 최종 LLM은 꼬리질문만 채점하고 최초 서버 확정 점수와 합산 | 일부 대체 (문항 번호·배점·합격선은 ADR-023, 단독 채점 전략은 유지) |
| [ADR-015](ADR-015-badge-assets-served-by-application.md) | 뱃지 자산을 애플리케이션 정적 리소스와 상대 URL로 배포 | 일부 대체됨 (운영은 [ADR-022](ADR-022-badge-images-private-s3-presigned-get.md), 로컬 fallback 유지) |
| [ADR-016](ADR-016-user-withdrawal-cascade-delete.md) | 회원 탈퇴: DB ON DELETE CASCADE로 전체 즉시 삭제 (resume/interview/message/judgment 도메인 교차 영향) | 제안 (FE #5 대응) |
| [ADR-017](ADR-017-oauth2-callback-fragment-redirect.md) | 로그인 콜백: accessToken을 URL fragment로 실어 프론트로 리다이렉트 | 제안 (이슈 #96, FE #3 대응) |
| [ADR-018](ADR-018-badge-image-public-access.md) | 뱃지 정적 이미지 경로(/badges/**)를 인증 없이 공개, /api/**는 유지 | 일부 대체됨 (운영은 [ADR-022](ADR-022-badge-images-private-s3-presigned-get.md), 로컬 fallback 유지) |
| [ADR-019](ADR-019-resume-expiration-preserves-history.md) | Resume 만료 시 레코드를 유지하고 추출 텍스트만 삭제 | ✅ 승인 (이슈 #108, PR #120) |
| [ADR-020](ADR-020-user-profile-image-s3.md) | 마이페이지 프로필 이미지: 사용자 업로드 + S3 저장(private 버킷, Presigned GET) | 제안 (이슈 #98, FE #10 대응) |
| [ADR-021](ADR-021-resume-deletion-history-preservation.md) | 개별 이력서 삭제 시 면접 히스토리 보존 | 제안 (RS-004) |
| [ADR-022](ADR-022-badge-images-private-s3-presigned-get.md) | 뱃지 이미지: private S3 + BG-001 Presigned GET | 제안 (BE #132, FE #42) |
| [ADR-023](ADR-023-five-question-level-passing-score.md) | 5문항·문항당 20점 채점과 레벨별 합격선 | 제안 (이슈 #147, PR #148 선행) |
| [ADR-024](ADR-024-defer-interview-status-polling-endpoint.md) | 면접 처리 상태 전용 폴링 API 도입 연기 (MVP, IS-002 동기 방식 유지) | ✅ 승인 (ADR-004 후속, 이슈 #85 대응) |
| [ADR-025](ADR-025-scoring-input-boundary-contextual-report-fallback.md) | 채점 입력 신뢰 경계와 실제 면접 기반 리포트 대체 | 제안 (이슈 #170, #179) |

모든 ADR은 기획서 v5.1 16장 "기술적 의사결정 근거" 표(5개 결정)와 관련 장(6·7·8·9·17장)을
근거로 작성했습니다. 세부 모델 선정처럼 아직 열려 있는 하위 결정은 해당 ADR 본문에
⚠️ TBD로 표시했습니다. 새 ADR을 작성할 때는 `ADR-002-ai-agent-harness-engineering.md`의
구조(배경/결정/대안 및 반려/결과)를 그대로 따르세요.
