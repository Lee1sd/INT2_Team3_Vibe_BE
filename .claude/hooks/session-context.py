#!/usr/bin/env python
"""SessionStart hook: docs/ai/SHARED.md 전체를 세션 컨텍스트에 자동 주입한다.

새 세션 시작, --continue/--resume으로 세션을 이어갈 때, /clear 이후 모두 실행된다.
docs/ai/owners/*.md는 의도적으로 주입하지 않는다 (docs/ai/setup-rules.md §1 참고) —
담당자 파일은 본인이 세션마다 직접 @ 멘션해야 한다.
"""
import json
import os
import sys


def find_project_root() -> str:
    env_root = os.environ.get("CLAUDE_PROJECT_DIR")
    if env_root and os.path.isdir(env_root):
        return env_root
    return os.getcwd()


def main() -> None:
    try:
        json.load(sys.stdin)
    except Exception:
        pass

    root = find_project_root()
    shared_path = os.path.join(root, "docs", "ai", "SHARED.md")

    if not os.path.isfile(shared_path):
        context = (
            "[커리어 던전 하네스] docs/ai/SHARED.md 를 찾을 수 없습니다. "
            "경로가 맞는지 확인하세요: " + shared_path
        )
    else:
        with open(shared_path, encoding="utf-8") as f:
            shared = f.read()
        context = (
            "[커리어 던전 하네스 — 세션 시작 자동 주입]\n\n"
            + shared
            + "\n\n---\n"
            "⚠️ 본인 owner 파일은 자동 로드되지 않습니다. "
            "작업을 시작하기 전에 반드시 @docs/ai/owners/<본인 이름>.md 를 직접 멘션하세요.\n"
        )

    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "SessionStart",
                    "additionalContext": context,
                }
            }
        )
    )


if __name__ == "__main__":
    main()
