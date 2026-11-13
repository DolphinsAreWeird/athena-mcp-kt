"""Write pages, chunks, vectors and the change log to Postgres (schema: ../db/schema.sql)."""
from __future__ import annotations

from pathlib import Path

import numpy as np
import psycopg

from .changelog import ChangeRow
from .chunking import Chunk
from .okb import Page
from .programmes import handbook_name, programme_name

SCHEMA = Path(__file__).resolve().parents[2] / "db" / "schema.sql"


def connect(url: str) -> psycopg.Connection:
    return psycopg.connect(url, autocommit=False)


def apply_schema(conn: psycopg.Connection, schema: Path = SCHEMA) -> None:
    conn.execute(schema.read_text(encoding="utf-8"))
    conn.commit()


def stored_hashes(conn: psycopg.Connection) -> dict[str, str]:
    return dict(conn.execute("SELECT id, content_hash FROM sections").fetchall())


def vector_literal(v: np.ndarray) -> str:
    return "[" + ",".join(f"{x:.7g}" for x in v.tolist()) + "]"


def sync(
    conn: psycopg.Connection,
    pages: list[Page],
    changed: dict[str, tuple[str, list[Chunk], np.ndarray | None]],   # id -> (hash, chunks, vectors)
    changes: list[ChangeRow],
    okb_root: Path,
    okb_commit: str | None,
    model: str | None,
) -> dict[str, int]:
    """One transaction: the server never sees a half-loaded index."""
    with conn.transaction():
        cur = conn.cursor()
        for code in sorted({p.programme for p in pages}):
            cur.execute(
                """INSERT INTO programmes (code, name, handbook) VALUES (%s, %s, %s)
                   ON CONFLICT (code) DO UPDATE SET name = excluded.name, handbook = excluded.handbook""",
                (code, programme_name(code), handbook_name(code)),
            )
        keep = [p.id for p in pages]
        cur.execute("DELETE FROM sections WHERE NOT (id = ANY(%s))", (keep,))
        removed = cur.rowcount

        by_id = {p.id: p for p in pages}
        for page_id, (content_hash, chunks, vectors) in changed.items():
            p = by_id[page_id]
            cur.execute("DELETE FROM sections WHERE id = %s", (p.id,))     # cascades to its chunks
            cur.execute(
                """INSERT INTO sections (id, programme, path, title, type, description, section_no, handbook,
                                         source_document, updated_on, applies_to, tags, body, content_hash)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                (p.id, p.programme, p.path, p.title, p.type, p.description, p.section_no, p.handbook,
                 p.source_document, p.updated_on, p.applies_to, p.tags, p.body, content_hash),
            )
            cur.executemany(
                """INSERT INTO chunks (section_id, ord, section_title, heading, text, embedding)
                   VALUES (%s, %s, %s, %s, %s, %s::vector)""",
                [(p.id, c.ord, p.title, c.heading, c.text,
                  None if vectors is None else vector_literal(vectors[i]))
                 for i, c in enumerate(chunks)],
            )

        programme_of = {p.path: p.programme for p in pages}
        title_of = {p.path: p.title for p in pages}
        cur.executemany(
            """INSERT INTO changes (commit, committed_at, author, action, title, path, section_id, programme, status)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
               ON CONFLICT (commit, path) DO UPDATE SET title = excluded.title, programme = excluded.programme""",
            [(c.commit, c.committed_at, c.author, c.action, c.title or title_of.get(c.path), c.path, c.section_id,
              programme_of.get(c.path) or _programme_from_path(c.path), c.status)
             for c in changes],
        )

        counts = cur.execute(
            "SELECT (SELECT count(*) FROM sections), (SELECT count(*) FROM chunks),"
            " (SELECT count(*) FROM chunks WHERE embedding IS NOT NULL)").fetchone()
        cur.execute(
            "INSERT INTO ingest_runs (okb_root, okb_commit, model, sections, chunks, embedded) VALUES (%s,%s,%s,%s,%s,%s)",
            (str(okb_root), okb_commit, model, *counts),
        )
    return {"sections": counts[0], "chunks": counts[1], "embedded": counts[2], "updated": len(changed),
            "removed": removed, "changes": len(changes)}


def _programme_from_path(path: str) -> str:
    folder = path.split("/", 1)[0]
    return "general" if folder == "minutes" else folder
