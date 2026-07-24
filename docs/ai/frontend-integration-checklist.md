# 프론트-백엔드 API 연동 확인 체크리스트

> 월요일 표지민 프론트 연동 시, "완료로 보였지만 실제 연결이 없었음" 유형을 빠르게 걸러내기 위한 확인 목록이다.  
> 기준 문서: `docs/api/api-spec.md`, `docs/api/api-contract.md`

## 준비

- 백엔드 서버를 실행한다.
- 인증이 필요한 API이므로 프론트 로그인 흐름 또는 테스트 토큰으로 `Authorization: Bearer <ACCESS_TOKEN>`을 준비한다.
- RS-001로 업로드해 `parseStatus=DONE`이 된 `resumeId`와 사용 가능한 `interviewerId`, `keyword`를 준비한다.

```bash
BASE_URL="http://localhost:8080"
TOKEN="replace-with-access-token"
RESUME_ID=501
INTERVIEWER_ID=1
KEYWORD="DB"
```

## 1. 응답 필드명 일치 확인

### IS-001 질문 생성 응답

확인 대상:

- `sessionId`: Number(Long)
- `status`: String, 최초 생성 직후 `IN_PROGRESS`
- `questions[]`: Array, 길이 4
- `questions[].questionId`: Number(Integer), 세션 안의 turn 값 `1`, `2`, `3`, `4`
- `questions[].question`: String
- `questions[].questionText`, `id`, `messageId` 같은 대체 필드명이 섞이지 않는지 확인

한 번만 호출해 응답 저장:

```bash
curl -s -X POST "$BASE_URL/api/interviews" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"resumeId\": $RESUME_ID,
    \"interviewerId\": $INTERVIEWER_ID,
    \"keyword\": \"$KEYWORD\"
  }" > interview-create-response.json

cat interview-create-response.json | jq
SESSION_ID=$(jq -r '.sessionId' interview-create-response.json)
```

저장된 응답 검증:

```bash
jq '{
    sessionIdType: (.sessionId | type),
    status,
    questionCount: (.questions | length),
    questionIds: [.questions[].questionId],
    questionFieldTypes: [.questions[] | {questionId: (.questionId | type), question: (.question | type)}],
    unexpectedQuestionText: ([.questions[] | has("questionText")] | any),
    unexpectedId: ([.questions[] | has("id")] | any),
    unexpectedMessageId: ([.questions[] | has("messageId")] | any)
  }' interview-create-response.json
```

판정 기준:

- `questionIds`가 `[1,2,3,4]`여야 한다.
- 각 문항은 `questionId`, `question` 필드명을 사용해야 한다.
- `unexpectedQuestionText=false`, `unexpectedId=false`, `unexpectedMessageId=false`여야 한다.
- `questionId`가 DB `Message.id`처럼 큰 숫자로 나오면 실패로 본다.

### IS-002 답변 제출 응답

확인 대상:

- 최초 4답변 제출 응답:
  - `evaluations[]`: Array, 길이 4
  - `evaluations[].questionId`: Number(Integer), `1`, `2`, `3`, `4`
  - `evaluations[].score`: Number(Integer)
  - `evaluations[].feedback`: String
  - `totalScore`: Number(Integer), 최초 응답은 0~80
  - `weakestQuestionId`: Number(Integer), 최초 응답에는 존재해야 함
  - `passed`: Boolean
  - `nextTurn.type`: String, `FOLLOW_UP`
  - `nextTurn.targetQuestionId`: Number(Integer)
  - `nextTurn.question`: String
- 꼬리질문 답변 제출 응답:
  - `evaluations[]`: Array, 최종 응답은 turn 1~5 포함
  - `totalScore`: Number(Integer), 최종 응답은 0~100
  - `passed`: Boolean
  - `overallFeedback`: String
  - `nextTurn`: `null` 또는 미노출 여부를 프론트 파서와 맞춘다.
  - 최종 응답에는 `weakestQuestionId`가 없어도 정상이다.

최초 4답변 제출은 한 번만 호출해 저장:

```bash
curl -s -X POST "$BASE_URL/api/interviews/$SESSION_ID/answers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "answers": [
      { "questionId": 1, "answerText": "1번 답변" },
      { "questionId": 2, "answerText": "2번 답변" },
      { "questionId": 3, "answerText": "3번 답변" },
      { "questionId": 4, "answerText": "4번 답변" }
    ]
  }' > initial-answer-response.json

cat initial-answer-response.json | jq
```

저장된 최초 응답 필드 검증:

```bash
jq '{
    evaluationCount: (.evaluations | length),
    evaluationTypes: [.evaluations[] | {questionId: (.questionId | type), score: (.score | type), feedback: (.feedback | type)}],
    totalScoreType: (.totalScore | type),
    weakestQuestionIdType: (.weakestQuestionId | type),
    passedType: (.passed | type),
    nextTurn
  }' initial-answer-response.json
```

꼬리질문 답변 제출은 한 번만 호출해 저장:

```bash
curl -s -X POST "$BASE_URL/api/interviews/$SESSION_ID/answers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "answers": [
      { "questionId": 5, "answerText": "꼬리질문 답변" }
    ]
  }' > final-response.json

cat final-response.json | jq
```

저장된 최종 응답 필드 검증:

```bash
jq '{
    evaluationCount: (.evaluations | length),
    questionIds: [.evaluations[].questionId],
    totalScoreType: (.totalScore | type),
    hasWeakestQuestionId: has("weakestQuestionId"),
    passedType: (.passed | type),
    overallFeedbackType: (.overallFeedback | type),
    nextTurn
  }' final-response.json
```

판정 기준:

- 프론트 기대 필드명과 실제 JSON 필드명이 정확히 같아야 한다.
- 숫자 필드는 JSON number, `passed`는 JSON boolean이어야 한다.
- `questionId`는 외부 API 기준 turn 값이다. DB 내부 id를 프론트 상태 키로 쓰지 않는다.
- 같은 세션에 같은 POST를 두 번 보내지 않는다. 필드 검증은 저장된 JSON 파일에 `jq`를 다시 실행한다.

## 2. 세부점수 미노출 원칙 확인

확인 대상:

- 응답 JSON 어디에도 5개 세부 루브릭 필드가 없어야 한다.
- 금지 필드/문구 예시:
  - `technicalAccuracy`
  - `coreContentSatisfaction`
  - `reasoningProcess`
  - `specificityPracticality`
  - `tradeoffException`
  - `기술적정확성`
  - `핵심내용충족도`
  - `근거판단과정`
  - `구체성실무연계`
  - `트레이드오프예외`
- `feedback`, `overallFeedback` 텍스트에도 `"기술적정확성 8점"`처럼 세부점수를 직접 암시하는 문구가 섞이면 안 된다.

실제 응답 JSON 확인:

```bash
cat initial-answer-response.json | jq
cat final-response.json | jq
```

금지 키/문구 검색:

```bash
PROHIBITED_RUBRIC_PATTERN="technicalAccuracy|coreContentSatisfaction|reasoningProcess|specificityPracticality|tradeoffException|기술적정확성|핵심내용충족도|근거판단과정|구체성실무연계|트레이드오프예외"

grep -E "$PROHIBITED_RUBRIC_PATTERN" initial-answer-response.json
grep -E "$PROHIBITED_RUBRIC_PATTERN" final-response.json
```

판정 기준:

- 두 `grep` 모두 결과가 없어야 한다.
- 최초 응답과 최종 응답 모두 문항별 `score`, `feedback`, 최종 `totalScore`, `passed`, `overallFeedback` 수준만 노출되어야 한다.

## 3. 기타 연동 포인트

### 세션 상태값

확인 대상:

- IS-001 직후: `IN_PROGRESS`
- 최초 4답변 제출 후: 백엔드 내부 상태는 `AWAITING_FOLLOWUP`
- 꼬리질문 답변 제출 후: 백엔드 내부 상태는 `COMPLETED`

프론트 확인 방법:

- IS-001 응답의 `status`가 `IN_PROGRESS`인지 확인한다.
- IS-002 최초 응답에서 `nextTurn.type=FOLLOW_UP`이면 프론트 상태를 꼬리질문 대기로 전환한다.
- IS-002 최종 응답에 `passed`, `totalScore`, `overallFeedback`이 있고 `nextTurn`이 `null` 또는 미노출이면 완료 화면으로 전환한다.

주의:

- 현재 공개 응답에서 모든 단계의 내부 `InterviewSession.status`를 직접 반환하지 않을 수 있다.
- 프론트 enum은 `IN_PROGRESS`, `AWAITING_FOLLOWUP`, `COMPLETED` 철자를 기준으로 맞춘다.

### 에러 응답 형식

CM-002 확정 포맷:

```json
{
  "code": "RESUME_TYPE_LIMIT_EXCEEDED",
  "message": "이력서(RESUME)는 최대 3개까지만 업로드할 수 있습니다.",
  "status": 400
}
```

확인 대상:

- 성공 응답에는 공통 래퍼를 씌우지 않는다. 즉 `data`, `success` 최상위 래퍼가 없어야 한다.
- 에러 응답은 최상위에 `code`, `message`, `status`만 기본으로 사용한다.
- JSON 바디의 `status`는 실제 HTTP status와 같은 숫자여야 한다.

에러 호출 및 HTTP status/바디 저장:

```bash
HTTP_STATUS=$(curl -s -o error-response.json -w "%{http_code}" -X POST "$BASE_URL/api/interviews" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"resumeId\": $RESUME_ID,
    \"interviewerId\": $INTERVIEWER_ID,
    \"keyword\": \"\"
  }")

cat error-response.json | jq
echo "HTTP_STATUS=$HTTP_STATUS"
```

에러 바디와 상태 코드 검증:

```bash
BODY_STATUS=$(jq -r '.status' error-response.json)

jq '{
    code: (.code | type),
    message: (.message | type),
    status: (.status | type),
    hasDataWrapper: has("data"),
    hasSuccessWrapper: has("success")
  }' error-response.json

test "$HTTP_STATUS" = "$BODY_STATUS" && echo "status matches" || {
  echo "status mismatch: http=$HTTP_STATUS body=$BODY_STATUS"
  exit 1
}
```

판정 기준:

- `code`, `message`는 string, `status`는 number여야 한다.
- `hasDataWrapper=false`, `hasSuccessWrapper=false`여야 한다.
- 실제 HTTP status와 JSON 바디의 `.status` 값이 같아야 한다.

## 완료 기준

- IS-001 응답 필드명/타입이 프론트 DTO와 일치한다.
- IS-002 최초/최종 응답 필드명/타입이 프론트 DTO와 일치한다.
- 5개 세부 루브릭 점수가 API 응답과 피드백 텍스트에 노출되지 않는다.
- 프론트 상태 전환이 `IN_PROGRESS → AWAITING_FOLLOWUP → COMPLETED` 흐름과 충돌하지 않는다.
- 에러 응답이 CM-002 포맷(`code`, `message`, `status`)으로 파싱되고, HTTP status와 바디 `status`가 일치한다.
