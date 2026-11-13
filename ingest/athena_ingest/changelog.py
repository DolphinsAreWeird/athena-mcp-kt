"""The OKB change log: Athena commits every approved change to git as
"Approve: Title (path)", "Add new page: Title (path)", "Delete page: ...", "Undo: ...".
We read `git log` for the OKB folder and keep one row per (commit, page).
"""
from __future__ import annotations

import datetime as dt
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

SUBJECT = re.compile(r"^(?P<action>[A-Z][A-Za-z ]{1,40}?): (?P<title>.+) \((?P<path>[^()]+\.md)\)\s*$")
RECORD, UNIT = "\x1e", "\x1f"


@dataclass
class ChangeRow:
    commit: str
    committed_at: dt.datetime
    author: str
    action: str
    title: str | None
    path: str
    status: str          # A / M / D

    @property
    def section_id(self) -> str:
        return self.path.removesuffix(".md")


def parse_subject(subject: str) -> tuple[str, str, str] | None:
    m = SUBJECT.match(subject.strip())
    return (m["action"], m["title"], m["path"]) if m else None


def parse_git_log(output: str) -> list[ChangeRow]:
    rows: list[ChangeRow] = []
    for record in output.split(RECORD):
        if not record.strip():
            continue
        header, _, files = record.partition("\n")
        sha, date, author, subject = header.split(UNIT, 3)
        parsed = parse_subject(subject)
        for line in files.splitlines():
            if not line.strip():
                continue
            status, _, path = line.partition("\t")
            if not path.endswith(".md") or "/" not in path:
                continue
            if parsed:
                action, title, subject_path = parsed
                title = title if subject_path == path else None
            else:
                action, title = "Bulk edit", None
            rows.append(ChangeRow(sha[:7], dt.datetime.fromisoformat(date), author, action, title, path, status[0]))
    return rows


def read_changelog(okb_root: Path) -> list[ChangeRow]:
    """Empty if the OKB folder isn't inside a git repository."""
    try:
        out = subprocess.run(
            ["git", "-C", str(okb_root), "log", "--no-renames", "--relative", "--name-status",
             f"--format={RECORD}%H{UNIT}%cI{UNIT}%an{UNIT}%s", "--", "."],
            check=True, capture_output=True, text=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return []
    return parse_git_log(out)


def head_commit(okb_root: Path) -> str | None:
    try:
        return subprocess.run(["git", "-C", str(okb_root), "rev-parse", "--short", "HEAD"],
                              check=True, capture_output=True, text=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
