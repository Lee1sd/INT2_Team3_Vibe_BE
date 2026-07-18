# api-spec.md — API 명세서

> 원본: `WBS_Vibe v5.2 - api 명세서.csv` (v5.2에서 내용 변경 없음). 표 형식만 마크다운으로 바꿨고 내용은 그대로
> 옮겼습니다. 공통 응답 포맷/에러 코드는 `docs/ai/owners/pyo-jimin.md`의 위임 규칙에 따라
> 표지민이 확정하는 `api-contract.md`를 따로 둡니다.

## 인증 (Auth) — Google OAuth2 + JWT

### AU-001 — GET `/api/auth/oauth2/google`

- 설명: 구글 소셜 로그인 시작
- 인증 필요: No
- 상태 코드: 302
- 비고: 커스텀 경로 사용 (Google 로그인 화면으로 302 리다이렉트)

### AU-002 — GET `/api/auth/oauth2/callback/{registrationId}`

- 설명: 구글 로그인 콜백 처리 → JWT 발급 (`{registrationId}`는 `google` 등 OAuth2 provider 식별자,
  `SecurityConfig.OAUTH2_CALLBACK_BASE_URI` 및 `redirect-uri` 설정과 일치해야 함)
- Request: `code`, `state` (Google 자동 전달)
- Response 예시:

```json
{
  "accessToken": "eyJhbGciOi...",
  "user": {
    "id": 1,
    "name": "이건희",
    "email": "lee@example.com"
  }
}
```

- 인증 필요: No / 상태 코드: 200
- 비고: Refresh Token은 HttpOnly/Secure/Strict 쿠키로 별도 발급. Access Token 30분 만료

### AU-003 — POST `/api/auth/refresh`

- 설명: Access Token 재발급
- Request: (Refresh Token 쿠키 자동 전송)
- Response 예시:

```json
{
  "accessToken": "eyJhbGciOi..."
}
```

- 인증 필요: Cookie / 상태 코드: 200/401
- 비고: 자동 재발급 + 로테이션

### AU-004 — POST `/api/auth/logout`

- 설명: 로그아웃
- Response 예시:

```json
{
  "message": "로그아웃 되었습니다"
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 쿠키 삭제 + 서버측 Refresh Token 무효화

## 유저 프로필 (User Profile) — 7/9 신규(개인화)

### UP-001 — PATCH `/api/users/me`

- 설명: 마이페이지에서 이름 수정
- Request: `{"name": "홍길동"}`
- Response 예시:

```json
{
  "id": 1,
  "name": "홍길동"
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 수정된 이름은 이후 질문/피드백 생성 시 프롬프트에 반영됨(예: "OO님, ...")

## 이력서/포트폴리오 (Resume)

### RS-001 — POST `/api/resumes`

- 설명: 이력서/포트폴리오 업로드 및 텍스트 추출 (type 파라미터로 구분)
- Request:

```json
{
  "type": "RESUME",
  "file": "(multipart)"
}
```

- Response 예시:

```json
{
  "resumeId": 501,
  "type": "RESUME",
  "parseStatus": "PROCESSING"
}
```

- 인증 필요: Yes / 상태 코드: 201/400
- 비고: `type=RESUME`(필수, 최소 1개~최대 3개)/`PORTFOLIO`(선택, 최대 3개) — ✅ 2026-07-10
  확정(마이페이지 와이어프레임 `06-mypage.svg` 기준, `docs/requirements/open-questions.md` #1).
  동일 `type` 파일이 이미 3개면 추가 업로드는 거부(400)하거나 UI에서 교체를 유도한다 —
  구체 교체 UX는 이건희 구현 시 결정. 추출 시 PII 마스킹 처리(✅ 이메일만 마스킹으로 확정
  — `docs/requirements/open-questions.md` #7)

### RS-002 — GET `/api/resumes/{resumeId}`

- 설명: 파싱 상태 조회 (폴링)
- Response 예시:

```json
{
  "resumeId": 501,
  "type": "RESUME",
  "parseStatus": "DONE",
  "extractedText": "(마스킹 처리됨, 생략)"
}
```

- 인증 필요: Yes / 상태 코드: 200/404
- 비고: `parseStatus`: PROCESSING/DONE/FAILED

### RS-003 — GET `/api/resumes`

- 설명: 로그인한 사용자가 업로드한 이력서/포트폴리오 전체 목록 조회
- Response 예시:

```json
[
  {
    "resumeId": 501,
    "type": "RESUME",
    "parseStatus": "DONE",
    "lastUploadedAt": "2026-07-15T10:00:00Z"
  }
]
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 응답은 배열이며, 항목별 `extractedText`는 포함하지 않는다(목록 조회 용도 —
  본문은 RS-002로 개별 조회). `parseStatus`가 PROCESSING/DONE/FAILED인 이력서를
  필터링하지 않고 모두 포함한다(FAILED 상태의 이력서는 사용자가 재업로드 필요 여부를
  확인할 수 있도록 노출). 목록은 `lastUploadedAt` 내림차순(최신순)으로 정렬한다. `type`
  필터링(`?type=RESUME`) 등 쿼리 파라미터는 아직 미정 — 필요 시 이 문서에 갱신한다.

## 키워드 (Keyword)

### KW-001 — GET `/api/keywords`

- 설명: 선택 가능한 키워드 목록 조회
- Response 예시:

```json
{
  "keywords": ["DB", "보안"]
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: MVP는 2개만 노출 권장(DB, 보안)

## 면접관 (Interviewer/PersonaConfig)

### IV-001 — GET `/api/interviewers`

- 설명: 면접관(페르소나) 리스트 및 해금 정보
- Response 예시:

```json
{
  "interviewers": [
    { "id": 1, "name": "널널한 대리", "level": 1, "tone": "lenient", "unlocked": true, "comingSoon": false },
    { "id": 2, "name": "깐깐한 과장", "level": 2, "tone": "strict", "unlocked": false, "comingSoon": false },
    { "id": 3, "name": "압박 페르소나", "level": 3, "tone": "pressure", "unlocked": false, "comingSoon": true }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: MVP는 Lv.1/Lv.2만 실제 기능 동작. Lv.3은 `comingSoon=true`로 UI에 회색/잠금
  표시만 하고 실제 면접 진행 로직은 없음(프론트 전용 placeholder)

## 뱃지 (Badge)

### BG-001 — GET `/api/badges/me`

- 설명: 내가 획득한 뱃지 목록 조회
- Response 예시:

```json
{
  "badges": [
    {
      "badgeId": 1,
      "stage": 1,
      "name": "프로그램 머쓱(초안)",
      "imageUrl": "...",
      "acquiredAt": "2026-07-08T10:00:00"
    }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 200
- ⚠️ 뱃지별 최종 `name`과 실제 배포 `imageUrl`은 아직 SSOT에 확정값이 없다. 응답 예시의
  이름과 `...` URL을 운영 seed로 사용하지 않으며, 디자인 자산 전달 위치와 공개 URL 계약을
  확정한 뒤 기준 데이터를 추가한다(`docs/requirements/open-questions.md` #10).
- 비고: ✅ 2026-07-10 팀 확인 완료 — 4단계 확정. 트리거는 "레벨을 클리어해서 `unlockedLevel`이
  N으로 올라가는 시점" 기준이다: 가입 직후(`unlockedLevel=1`, 별도 클리어 없이 기본 제공)=Stage1
  / Lv.1 클리어(`unlockedLevel=2`)=Stage2 / Lv.2 클리어(`unlockedLevel=3`)=Stage3 /
  Lv.3 클리어(`unlockedLevel=4`, 스트레치골)=Stage4. Lv.3·Lv.4는 모두 스트레치골이라 면접
  진행 로직은 아직 없지만(`IV-001`은 Lv.1~3까지만 노출, Lv.4는 API 명세에 아직 없음),
  뱃지 디자인 자체는 4단계 전부 이미 제작되어 있다(Stage1~4 아트웍 준비 완료).
  `docs/requirements/open-questions.md` #2 참고

## 채팅 히스토리 (History)

### HS-001 — GET `/api/interviews/history`

- 설명: 레벨별 채팅 히스토리 사이드바 조회
- Response 예시:

```json
{
  "levels": [
    {
      "level": 1,
      "sessions": [
        { "sessionId": 9001, "createdAt": "2026-07-08T10:00:00", "totalScore": 67 }
      ]
    }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 레벨별 폴더 구조로 세션 목록 반환. 기존 `InterviewSession`+`Message`로 충분

## 유저 진행도 (User Progress)

### UM-001 — GET `/api/users/me/progress`

- 설명: 메인페이지 전체 진행도 게이지 조회
- Response 예시:

```json
{
  "unlockedLevel": 2,
  "progressGauge": 30
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 레벨 클리어마다 누적되는 계정 전체 진행도. 세션별 판정점수(0~100)와는 별개 개념.
  ✅ 2026-07-10 확정 — Lv.1 클리어 +30% / Lv.2 클리어 +30% / Lv.3 클리어 +40% = 100%
  (균등 분배 아님, 레벨별 증가폭을 상수로 관리). `docs/requirements/open-questions.md` #3 참고

## 면접 세션 (Interview Session)

### IS-001 — POST `/api/interviews`

- 설명: 면접 세션 생성 + 질문 3개 일괄 생성 (keyword 포함)
- Request:

```json
{
  "resumeId": 501,
  "interviewerId": 1,
  "keyword": "DB"
}
```

- Response 예시:

```json
{
  "sessionId": 9001,
  "status": "IN_PROGRESS",
  "questions": [
    { "questionId": 1, "question": "이 프로젝트에서 캐싱 전략을 선택한 이유는?" },
    { "questionId": 2, "question": "동시성 문제는 어떻게 처리했나요?" },
    { "questionId": 3, "question": "장애 발생 시 복구 전략은?" }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 201
- 비고: `resumeId`는 반드시 `type=RESUME`만 허용. 질문 생성 시 참고 질문 예시(few-shot)를
  프롬프트에 포함해 실무형 품질 강화. 질문/피드백에 사용자 이름 반영(예: "OO님, ...").
  외부 `questionId`는 세션 안의 질문 순서인 `Message.turn`(1~4)이며, DB의
  `questions.messageId`/`Message.id`는 모범답변 조회를 위한 내부 영속 키로 노출하지 않는다.
  이 계약은 확정됐지만 PR #82에는 judgment 소비 계약만 포함되므로, 기존 IS-001 응답이
  `Message.id` 대신 turn을 반환하도록 바꾸는 작업은 Interview owner의 연결 PR에서 적용한다.

### IS-002 — POST `/api/interviews/{id}/answers`

- 설명: 답변 제출 (최초 3개 일괄 또는 꼬리질문 1개) — 상태에 따라 배치채점/최종판정 자동 분기
- Request(최초 3개 일괄):

```json
{
  "answers": [
    { "questionId": 1, "answerText": "..." },
    { "questionId": 2, "answerText": "..." },
    { "questionId": 3, "answerText": "..." }
  ]
}
```

- Response 예시:

```json
{
  "evaluations": [
    { "questionId": 1, "score": 20, "feedback": "..." },
    { "questionId": 2, "score": 25, "feedback": "..." },
    { "questionId": 3, "score": 15, "feedback": "부족한 부분: ..." }
  ],
  "totalScore": 60,
  "weakestQuestionId": 3,
  "passed": false,
  "nextTurn": {
    "type": "FOLLOW_UP",
    "targetQuestionId": 3,
    "question": "그 캐싱 전략에서 정합성 문제는 어떻게 처리하셨나요?"
  }
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 채점 기준 5개 세부항목(7/9 확정)으로 내부 계산 — 기술적정확성10 / 핵심내용충족도5
  / 근거판단과정4 / 구체성실무연계3 / 트레이드오프예외3 = 25점. 단 API 응답·화면엔
  세부점수 노출 안 함, `score`+`feedback`(문장)만 제공. 최초 `totalScore`는 세 문항 합계
  0~75점이며 최종 IS-002b에서 네 문항 합계 0~100점을 반환한다(% 환산 없음). 모범답변은
  질문 생성 호출(FR-03) 시 생성해 `questions` 테이블(`messageId` 단일 PK/FK)에 저장해
  두고, 채점 호출은 해당 질문 `Message.id`로 저장된 값을 조회해 사용자 답변과 비교한다
  (새로 생성하지 않음 — `docs/requirements/open-questions.md` #9, 키 설계는 2026-07-14
  `messageId` 기준으로 번복). 서버 확정 최초 점수와 개별 피드백은 `answer_scores`에
  `(sessionId, turn)` 단위로 보존하며, 같은 문항의 중복 채점은 DB UNIQUE로 차단한다.
  interview 계층은 채점 및 꼬리질문 생성 LLM 호출을 DB 트랜잭션 밖에서 수행하고,
  호출 전후의 짧은 트랜잭션에서 세션 상태와 중복 결과를 다시 확인해야 한다. judgment는
  전달받은 LLM 원시 평가값부터 루브릭 적용·점수 영속화·판정을 담당한다. PR #82는
  judgment 소비 계약까지만 제공하며 엔드포인트 오케스트레이션은 Interview owner가 연결한다.

### IS-002b — POST `/api/interviews/{id}/answers` (2번째 호출 예시: 꼬리질문 답변 제출 → 최종 판정)

- Request:

```json
{
  "answers": [
    { "questionId": 4, "answerText": "..." }
  ]
}
```

- Response 예시:

```json
{
  "evaluations": [
    { "questionId": 1, "score": 20 },
    { "questionId": 2, "score": 25 },
    { "questionId": 3, "score": 15 },
    { "questionId": 4, "score": 22, "feedback": "..." }
  ],
  "totalScore": 82,
  "passed": true,
  "overallFeedback": "전반적으로 논리 전개는 탄탄했으나 세 번째 답변에서 트레이드오프 고려가 부족했습니다.",
  "nextTurn": null
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고:
  - 질문 생성 LLM이 `questionId=4` 꼬리질문과 비노출 예상답변을 반환하면, interview 계층이
    `Message(role=QUESTION, turn=4)`와 `Question(messageId, expectedAnswer)`로 저장하고
    세션을 `AWAITING_FOLLOWUP`으로 전환한다.
  - `questionId=4`의 예상답변도 다른 문항과 동일하게 질문 생성 호출 시점에 `questions`
    테이블에 저장한다 — 최종 채점 호출은 새로 생성하지 않고 해당 꼬리질문 `Message.id`로
    저장된 값을 조회해 사용자 답변과 비교한다(`docs/requirements/open-questions.md` #9,
    키 설계는 2026-07-14 `messageId` 기준으로 번복).
  - 최종 LLM 채점에는 `questionId=4`의 질문·답변·예상답변 한 건만 전달한다. 최초 1~3번은
    서버에서 이미 확정·보존한 점수를 재사용하며 다시 채점하지 않는다.
  - 다만 종합 피드백 품질을 위해 최초 1~3번의 질문·사용자 답변·서버 확정 점수·개별
    피드백을 별도 읽기 전용 컨텍스트로 전달한다. 이 컨텍스트는 4번 채점이나 기존 점수
    변경에 사용하지 않는다.
  - 서버는 기존 1~3번 점수와 새로 clamp한 4번 점수를 합쳐 100점 만점 총점·합격 여부를
    계산한다. 응답 `evaluations`에는 기존 1~3번 점수와 신규 4번 점수를 모두 포함한다.
  - 최종 판정 저장, 진행도·순차 해금·뱃지 반영, 세션 `COMPLETED` 전이는 하나의
    트랜잭션에서 처리하며 어느 한 단계가 실패하면 turn 4 답변부터 모두 롤백한다.
  - interview 계층은 최종 LLM 호출을 DB 트랜잭션 밖에서 수행해야 한다. 호출 전 준비 단계와
    호출 후 반영 단계가 각각 세션을 잠그고 상태·기존 최초 점수를 재검증하며, 원시 평가값은
    judgment에 전달해 서버 점수·판정으로 변환한다.
  - `tierLabel`/`tierDescription` 필드 제거(정정) — 등급 텍스트는 레벨 숫자 기준
    프론트 정적 표기라 API 응답에 불필요

## 공통 / 인프라

### CM-001 — LLM 인터페이스 내부

- LLM 벤더: Claude(Haiku 4.5/Sonnet 4.6) 확정. Mock 모드 시 고정 JSON 반환.
- 비고: LLM 벤더는 Claude 확정(Gemini/GPT 탈락). 세부 모델(Haiku 4.5 vs Sonnet 4.6)은
  미정 — 2주차 실연동 전 프롬프트 테스트 후 확정 예정

### CM-002 — 공통 예외처리/응답 포맷

- 비고: 공통코드 오너 = ④번(표지민) 확정. 인증+프론트+인프라 겸임 중이나, 프론트 작업이
  상당 부분 완료되어 업무량 감당 가능하다고 본인 확인함(7/10 기준)

### CM-003 — S3 버킷 정책

- S3 접근 제어: 퍼블릭 액세스 전체 차단 + IAM Role 기반 접근 제어 확정
