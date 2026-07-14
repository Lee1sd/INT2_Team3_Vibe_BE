#!/usr/bin/env python
"""Stop hook: feat/*, fix/* 브랜치에서 에이전트 턴이 끝나면 [Retro] 한 줄을 처리한다.
docs/ai/SHARED.md §5 (Retro 형식) 참고. 비차단성 훅이다 — 세션을 강제로 이어가게 하지
않는다.

동작:
  1) stdin JSON의 last_assistant_message(방금 AI가 낸 마지막 응답)에 [Retro] 블록이
     이미 있으면, 그 블록을 파싱해서 docs/ai/workflows/generated/retro-raw.md에
     자동으로 append한다 (사람이 채팅 기록을 뒤져서 복붙할 필요가 없게 함).
     retro-raw.md는 브랜치를 넘나들며 계속 누적되는 "개인 로컬 원본"이라
     .gitignore 대상이다 — 여러 명이 동시에 같은 파일을 커밋하면 매 PR마다
     충돌하므로, 커밋되는 건 금요일에 사람이 손으로 정리하는
     docs/ai/workflows/generated/retro-week{N}.md뿐이다.
  2) [Retro] 블록을 찾지 못했으면(AI가 깜빡했을 때) 기존처럼 형식을 리마인드한다.
"""
import datetime
import json
import os
import re
import subprocess
import sys

RETRO_FORMAT = """[Retro] 룰 작동: ...
       | 튜닝 제안: ...
       | SSOT 동기화 필요: ...
       | Edge Case: ..."""

RETRO_BLOCK_PATTERN = re.compile(r"\[Retro\].*?Edge Case:[^\n]*", re.DOTALL)

RAW_LOG_RELATIVE_PATH = os.path.join(
    "docs", "ai", "workflows", "generated", "retro-raw.md"
)


def find_project_root() -> str:
    env_root = os.environ.get("CLAUDE_PROJECT_DIR")
    if env_root and os.path.isdir(env_root):
        return env_root
    return os.getcwd()


def run_git(args: list, root: str) -> str:
    try:
        result = subprocess.run(
            ["git"] + args,
            cwd=root,
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=5,
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except Exception:
        pass
    return ""


def current_branch(root: str) -> str:
    return run_git(["rev-parse", "--abbrev-ref", "HEAD"], root)


def current_author(root: str) -> str:
    return run_git(["config", "user.name"], root) or "unknown"


def extract_retro_block(last_assistant_message: str) -> str:
    if not isinstance(last_assistant_message, str):
        return ""
    match = RETRO_BLOCK_PATTERN.search(last_assistant_message)
    if not match:
        return ""
    lines = [line.strip() for line in match.group(0).splitlines()]
    return "\n".join(line for line in lines if line)


def append_to_raw_log(root: str, branch: str, author: str, block: str) -> None:
    path = os.path.join(root, RAW_LOG_RELATIVE_PATH)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    is_new = not os.path.exists(path)
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    with open(path, "a", encoding="utf-8") as f:
        if is_new:
            f.write(
                "# retro-raw.md — 개인 로컬 Retro 원본 (커밋 대상 아님)\n\n"
                "이 파일은 .gitignore 대상입니다. `.claude/hooks/retro-reminder.py`가\n"
                "세션 종료마다 자동으로 append합니다. 금요일에 이 내용을 바탕으로\n"
                "`docs/ai/workflows/templates/retro-week-body.md` 형식으로 정리해\n"
                "`docs/ai/workflows/generated/retro-week{N}.md`를 만들고, 그 파일을 커밋하세요.\n\n"
                "---\n\n"
            )
        f.write(f"## {timestamp} · {branch} · {author}\n\n{block}\n\n---\n\n")


def main() -> None:
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")

    try:
        data = json.load(sys.stdin)
    except Exception:
        data = {}

    if not isinstance(data, dict):
        data = {}

    if data.get("stop_hook_active"):
        return

    root = find_project_root()
    branch = current_branch(root)

    if not (branch.startswith("feat/") or branch.startswith("fix/")):
        return

    block = extract_retro_block(data.get("last_assistant_message", ""))

    if block:
        author = current_author(root)
        append_to_raw_log(root, branch, author, block)
        sys.stderr.write(
            "\n[retro-reminder] 이번 세션의 [Retro]를 "
            + RAW_LOG_RELATIVE_PATH.replace(os.sep, "/")
            + "에 자동 저장했습니다.\n"
        )
        return

    sys.stderr.write(
        "\n[retro-reminder] 현재 브랜치: "
        + branch
        + "\n작업을 마치기 전에 아래 형식으로 [Retro] 한 줄을 출력하세요"
        " (출력만 하면 이 훅이 자동으로 저장합니다):\n\n"
        + RETRO_FORMAT
        + "\n"
    )


if __name__ == "__main__":
    main()
