#!/usr/bin/env python
"""PostToolUse hook: 구조적 결정이 의심되는 코드 변경, 또는 PR/이슈 생성 직전에
"이거 ADR로 남겨야 하는 결정 아닌가?"를 상기시킨다. 코드를 막거나 되돌리지 않는다
(비차단성 훅) — docs/adr/how-to-write-adr.md §5, docs/ai/setup-rules.md 참고.

두 가지 트리거를 하나의 스크립트에서 처리한다:
  1) Edit/Write/MultiEdit: 구조/설정 파일 경로, 또는 결정을 암시하는 키워드 감지
  2) Bash: `gh pr create` / `gh issue create` 명령 감지
matcher는 .claude/settings.json에서 두 블록("Edit|Write|MultiEdit", "Bash")으로
나눠 등록하고, 이 스크립트는 tool_input의 모양으로 어느 쪽인지 스스로 판단한다.
"""
import json
import os
import re
import sys

ARCHITECTURE_SENSITIVE_PATH_FRAGMENTS = (
    os.path.normpath("build.gradle"),
    os.path.normpath("settings.gradle"),
    os.path.normpath("global/config/"),
    os.path.normpath("application.yml"),
    os.path.normpath("application.yaml"),
    os.path.normpath("application.properties"),
    os.path.normpath("Dockerfile"),
    os.path.normpath("docker-compose"),
    os.path.normpath(".github/workflows/"),
    os.path.normpath("LlmClient"),
)

DECISION_KEYWORDS = (
    "trade-off", "tradeoff", "트레이드오프",
    "대안", "채택", "반려", "vs.", " vs ",
    "폴링", "SSE", "MSA", "모놀리", "벤더",
    "선택했", "선택한", "결정했", "결정한",
)

GH_COMMAND_PATTERN = re.compile(r"gh\s+(pr|issue)\s+create")

CODE_CHANGE_REMINDER = """[ADR 제안 — adr-suggestion]
방금 수정한 내용이 구조적 결정(설정/빌드/배포/LLM 인터페이스 변경, 또는 "대안"/
"트레이드오프"/"채택" 같은 표현)과 관련된 것 같습니다. 아래 체크리스트로 ADR이
필요한 변경인지 확인하세요 (docs/adr/how-to-write-adr.md §3):
  - 이 결정을 나중에 바꾸려면 코드를 상당 부분 다시 써야 하는가?
  - 검토한 대안이 2개 이상이었는가?
  - 다른 도메인의 작업 방식에도 영향을 주는가?
필요하다고 판단되면 docs/adr/ADR-TEMPLATE.md를 복사해 ADR을 작성하세요.
해당 없으면 무시해도 됩니다 (이 훅은 참고용이며 강제하지 않습니다).
"""

GH_COMMAND_REMINDER = """[ADR 제안 — adr-suggestion]
PR/이슈를 올리기 전에 이 변경이 ADR로 남겨야 하는 결정을 포함하는지 한 번 더
확인하세요 (docs/adr/how-to-write-adr.md §3~4). 필요하면 PR에 ADR 파일을 함께
포함하거나, 이슈 본문에 "ADR 필요"를 표시하세요.
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


def extract_text_content(tool_input: dict) -> str:
    if not isinstance(tool_input, dict):
        return ""
    chunks = []
    for key in ("content", "new_string", "old_string"):
        value = tool_input.get(key)
        if isinstance(value, str):
            chunks.append(value)
    edits = tool_input.get("edits")
    if isinstance(edits, list):
        for edit in edits:
            if isinstance(edit, dict):
                for key in ("new_string", "old_string"):
                    value = edit.get(key)
                    if isinstance(value, str):
                        chunks.append(value)
    return "\n".join(chunks)


def touches_architecture_path(paths: list) -> bool:
    for raw_path in paths:
        normalized = os.path.normpath(raw_path)
        if any(fragment in normalized for fragment in ARCHITECTURE_SENSITIVE_PATH_FRAGMENTS):
            return True
    return False


def mentions_decision_keyword(text: str) -> bool:
    lowered = text.lower()
    return any(keyword.lower() in lowered for keyword in DECISION_KEYWORDS)


def is_gh_create_command(tool_input: dict) -> bool:
    if not isinstance(tool_input, dict):
        return False
    command = tool_input.get("command")
    if not isinstance(command, str):
        return False
    return bool(GH_COMMAND_PATTERN.search(command))


def emit(context: str) -> None:
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PostToolUse",
                    "additionalContext": context,
                }
            }
        )
    )


def main() -> None:
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")

    try:
        data = json.load(sys.stdin)
    except Exception:
        data = {}

    tool_input = data.get("tool_input", {}) if isinstance(data, dict) else {}

    # Bash 계열: gh pr/issue create 명령 감지
    if is_gh_create_command(tool_input):
        emit(GH_COMMAND_REMINDER)
        return

    # Edit/Write/MultiEdit 계열: 구조 변경 경로 또는 결정 키워드 감지
    paths = extract_file_paths(tool_input)
    text = extract_text_content(tool_input)
    if touches_architecture_path(paths) or mentions_decision_keyword(text):
        emit(CODE_CHANGE_REMINDER)
        return


if __name__ == "__main__":
    main()
