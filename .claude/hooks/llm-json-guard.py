#!/usr/bin/env python
"""PostToolUse hook: Edit/Write/MultiEdit로 LLM 연동 도메인 파일을 수정하면
'LLM 응답 방어' 체크리스트를 다시 상기시킨다. 코드를 막거나 되돌리지 않는다
(비차단성 훅) — docs/ai/setup-rules.md §2 참고.

FANDROPS의 concurrency-guard.py(order/inventory 수정 감지)와 동일한 패턴을
Career Dungeon의 최대 리스크(LLM 응답 불안정성)에 맞게 다시 구현한 것이다.
"""
import json
import os
import sys

GUARDED_PATH_FRAGMENTS = (
    os.path.normpath("domain/interview/"),
    os.path.normpath("domain/judgment/"),
    os.path.normpath("domain/progress/"),
    os.path.normpath("domain/persona/"),
    os.path.normpath("resources/prompts/"),
)

REMINDER = """[LLM 응답 방어 체크리스트 — llm-json-guard]
방금 수정한 파일은 LLM 연동/평가 경로에 포함됩니다. 커밋 전에 확인하세요:
  ⑤ LLM 응답이 스키마를 벗어나면(필드 누락, 점수 범위 초과) 어떻게 되는가?
     Mock 모드에서도 동일 분기가 동작하는가?
  ⑥ 예산 상한·타임아웃·재시도 횟수 제약을 지키는가?
자세한 내용: docs/ai/SHARED.md §3 (역방향 추적 6문항), 담당 owner 파일의 체크리스트.
"""


def extract_file_paths(tool_input: dict) -> list:
    paths = []
    if not isinstance(tool_input, dict):
        return paths
    for key in ("file_path", "path", "notebook_path"):
        value = tool_input.get(key)
        if isinstance(value, str):
            paths.append(value)
    edits = tool_input.get("edits")
    if isinstance(edits, list):
        for edit in edits:
            if isinstance(edit, dict) and isinstance(edit.get("file_path"), str):
                paths.append(edit["file_path"])
    return paths


def touches_guarded_path(paths: list) -> bool:
    for raw_path in paths:
        normalized = os.path.normpath(raw_path)
        if any(fragment in normalized for fragment in GUARDED_PATH_FRAGMENTS):
            return True
    return False


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception:
        data = {}

    tool_input = data.get("tool_input", {}) if isinstance(data, dict) else {}
    paths = extract_file_paths(tool_input)

    if not touches_guarded_path(paths):
        return

    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PostToolUse",
                    "additionalContext": REMINDER,
                }
            }
        )
    )


if __name__ == "__main__":
    main()
