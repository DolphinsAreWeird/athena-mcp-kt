"""Read an Athena OKB folder: one markdown page per handbook section, with YAML front matter.

Layout (Base_current / handbook-latex okb/):
    <root>/<collection>/[subfolders/]<page>.md     collection = programme code, 'general' or 'minutes'
Files directly in <root> (README.md, conflicts.md, gaps.md) are not pages and are skipped.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import re
from dataclasses import dataclass, field
from pathlib import Path

import yaml

from .programmes import handbook_name

FRONT_MATTER = re.compile(r"\A---\s*\n(.*?)\n---\s*\n?(.*)\Z", re.S)


@dataclass
class Page:
    id: str                      # path without .md: 'ece-ms/23-returning-from-a-leave-of-absence'
    path: str                    # 'ece-ms/23-returning-from-a-leave-of-absence.md'
    collection: str              # first folder: 'ece-ms', 'general', 'minutes'
    programme: str               # front matter `program`, else the collection
    title: str
    body: str                    # markdown without front matter and without the leading '# Title'
    handbook: str                # citation label
    content_hash: str
    type: str | None = None
    description: str | None = None
    section_no: str | None = None
    source_document: str | None = None
    updated_on: dt.date | None = None
    applies_to: list[str] = field(default_factory=list)   # [] = every programme
    tags: list[str] = field(default_factory=list)


def parse_page(text: str, rel_path: str) -> Page:
    m = FRONT_MATTER.match(text)
    meta, body = ({}, text) if not m else (yaml.safe_load(m.group(1)) or {}, m.group(2))
    collection = rel_path.split("/", 1)[0]
    programme = str(meta.get("program") or collection)
    title = str(meta.get("title") or Path(rel_path).stem.replace("-", " "))
    body = _strip_title(body, title)
    page_type = _str(meta.get("type"))
    source = _str(meta.get("source_document"))
    return Page(
        id=rel_path.removesuffix(".md"),
        path=rel_path,
        collection=collection,
        programme=programme,
        title=title,
        body=body.strip(),
        handbook=_handbook_label(programme, page_type, source),
        content_hash=hashlib.sha256(text.encode("utf-8")).hexdigest(),
        type=page_type,
        description=_str(meta.get("description")),
        section_no=_str(meta.get("section")),
        source_document=source,
        updated_on=_date(meta.get("timestamp")),
        applies_to=_applies_to(programme, meta.get("applies_to")),
        tags=[str(t) for t in (meta.get("tags") or [])],
    )


def load_okb(root: Path) -> list[Page]:
    root = root.resolve()
    pages = []
    for file in sorted(root.rglob("*.md")):
        rel = file.relative_to(root).as_posix()
        if "/" not in rel or any(part.startswith(".") for part in rel.split("/")):
            continue  # root-level files and hidden folders (.git) are not pages
        pages.append(parse_page(file.read_text(encoding="utf-8"), rel))
    return pages


def _strip_title(body: str, title: str) -> str:
    lines = body.lstrip().splitlines()
    if lines and lines[0].startswith("# "):
        lines = lines[1:]
    return "\n".join(lines)


def _applies_to(programme: str, raw) -> list[str]:
    """Normalise to: [] = applies everywhere; otherwise the programme codes it applies to."""
    if raw:
        codes = [str(c) for c in (raw if isinstance(raw, list) else [raw])]
        return [] if "general" in codes else codes
    return [] if programme == "general" else [programme]


def _handbook_label(programme: str, page_type: str | None, source: str | None) -> str:
    if page_type == "minutes":
        return "Committee minutes"
    if programme == "general":
        if source and source.startswith("http"):
            return f"CMKL website ({source})"
        return "CMKL University" + (f" ({source})" if source else "")
    return handbook_name(programme) + (f" ({source})" if source else "")


def _str(value) -> str | None:
    return None if value is None else str(value)


def _date(value) -> dt.date | None:
    if isinstance(value, dt.datetime):
        return value.date()
    if isinstance(value, dt.date):
        return value
    if isinstance(value, str):
        try:
            return dt.date.fromisoformat(value[:10])
        except ValueError:
            return None
    return None
