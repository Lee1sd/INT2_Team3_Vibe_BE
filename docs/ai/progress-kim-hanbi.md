# 김한비 진행 상태

이 문서는 `docs/ai/owners/kim-hanbi.md`에서 분리한 진행 상태 기록이다.
owner 파일에는 코드 오너 규칙, 담당 경로, 금지 경로, 참고 문서 목록만 둔다.

## 완료

- [x] 인터페이스 추상화 — `LlmClient` 인터페이스 + Mock 구현체 (이슈 #1, PR #2)
- [x] 응답 방어 — JSON 스키마 검증 + 재시도 (이슈 #3, PR #4)
- [x] 채점 프롬프트 — v2 확정, 모델 Haiku 4.5 확정 ([ADR-007](../adr/ADR-007-llm-model-selection-haiku45.md))
- [x] `EvaluationResponse` DTO 분리 — `InitialEvaluationResponse`/`FinalEvaluationResponse`
      (이슈 #12, PR #13 머지 완료, [ADR-008](../adr/ADR-008-evaluation-response-dto-split.md)).
      이슈 #6(IS-002b stale `weakestQuestionId` 검증 누락)도 이 분리로 타입 레벨 차단되어
      함께 해결·종료됨.
- [x] 페르소나 스타일 엔진 (대리/과장 말투) — `PersonaConfig`/`PersonaTone`/
      `PersonaPromptProvider` + 톤별 프롬프트 템플릿 구현 완료(이슈 #17, PR #22 머지 완료).
- [x] 질문 생성 프롬프트 실제 구현 — Question 엔티티/프롬프트/InterviewService 연동 완료
      (이슈 #45).
- [x] 꼬리질문 생성 — 최저점 문항 기반 후속 질문 생성 흐름 구현 및 main 반영 완료
      (이슈 #59, PR #68 머지 완료).
- [x] 최종 채점 계약 반영 — 이슈 #60/PR #62/ADR-014의 turn 4 단독 채점 계약을
      꼬리질문 흐름에 반영하고, turn 1~3은 `previousEvaluations` 컨텍스트로만 전달하도록 정리.

## 진행 중

- 현재 없음.

## 미착수

- 현재 없음.
