"""athena-ingest: load an Athena OKB into the athena-mcp-kt database.

    athena-ingest download-model                     # ~120 MB, once
    athena-ingest run --okb /path/to/base-current    # incremental: only changed pages are re-embedded
    athena-ingest run --okb ... --no-embed           # lexical search only, no model needed
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
from pathlib import Path

from . import changelog, chunking, embedding, okb, store

DEFAULT_DB = "postgresql://localhost:5432/athena"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="athena-ingest", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    dl = sub.add_parser("download-model", help="download the ONNX embedding model and tokenizer")
    dl.add_argument("--dir", type=Path, default=None, help=f"default: {embedding.default_model_dir()}")
    dl.add_argument("--onnx-file", default=embedding.DEFAULT_ONNX_FILE)

    run = sub.add_parser("run", help="ingest an OKB folder")
    run.add_argument("--okb", type=Path, default=os.environ.get("OKB_PATH"), required="OKB_PATH" not in os.environ,
                     help="OKB folder (or set OKB_PATH)")
    run.add_argument("--database-url", default=os.environ.get("DATABASE_URL", DEFAULT_DB))
    run.add_argument("--model-dir", type=Path, default=os.environ.get("ATHENA_MODEL_DIR"))
    run.add_argument("--no-embed", action="store_true", help="skip embeddings (lexical search only)")
    run.add_argument("--full", action="store_true", help="re-process every page, not just changed ones")

    parity = sub.add_parser("parity", help="write reference vectors the Kotlin test compares against")
    parity.add_argument("--model-dir", type=Path, default=os.environ.get("ATHENA_MODEL_DIR"))
    parity.add_argument("--out", type=Path, default=Path("fixtures/embedding-parity.json"))

    args = parser.parse_args(argv)
    if args.command == "download-model":
        path = embedding.download_model(args.dir, args.onnx_file)
        print(f"Model ready in {path}")
        return 0
    if args.command == "parity":
        return write_parity(args.model_dir, args.out)
    return run_ingest(args)


def run_ingest(args) -> int:
    started = time.monotonic()
    root = Path(args.okb).expanduser().resolve()
    pages = okb.load_okb(root)
    if not pages:
        print(f"No OKB pages found under {root}", file=sys.stderr)
        return 1

    embedder = None if args.no_embed else embedding.OnnxEmbedder(args.model_dir)
    model_tag = embedder.name if embedder else "no-embed"

    with store.connect(args.database_url) as conn:
        store.apply_schema(conn)
        stored = {} if args.full else store.stored_hashes(conn)
        changed = {}
        texts, owners = [], []
        for p in pages:
            # The hash covers the file, the chunker and the model: change any of them and the page is redone.
            h = hashlib.sha256(f"{p.content_hash}|{chunking.CHUNKER_VERSION}|{model_tag}".encode()).hexdigest()
            if stored.get(p.id) == h:
                continue
            chunks = chunking.chunk_section(p.body) or [chunking.Chunk(0, None, p.description or p.title)]
            changed[p.id] = (h, chunks, None)
            for c in chunks:
                texts.append(chunking.embedding_text(p.title, c))
                owners.append(p.id)

        if embedder and texts:
            print(f"Embedding {len(texts)} chunks from {len(changed)} pages ...", file=sys.stderr)
            vectors = embedder.embed(texts)
            offset = 0
            for page_id in changed:
                h, chunks, _ = changed[page_id]
                changed[page_id] = (h, chunks, vectors[offset:offset + len(chunks)])
                offset += len(chunks)

        counts = store.sync(conn, pages, changed, changelog.read_changelog(root), root,
                            changelog.head_commit(root), model_tag)

    counts["seconds"] = round(time.monotonic() - started, 1)
    print(json.dumps(counts))
    return 0


PARITY_TEXTS = [
    "How much notice must I give before returning from a leave of absence?",
    "Returning from a Leave of Absence. Students must notify the CUBE office before the semester starts.",
    "นักศึกษาต้องแจ้งล่วงหน้ากี่วันก่อนกลับมาเรียนหลังลาพัก",
    "CMKL",
]


def write_parity(model_dir: Path | None, out: Path) -> int:
    embedder = embedding.OnnxEmbedder(model_dir)
    vectors = embedder.embed(PARITY_TEXTS)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(
        {"model": embedder.name, "cases": [{"text": t, "vector": [round(float(x), 6) for x in v]}
                                           for t, v in zip(PARITY_TEXTS, vectors)]},
        ensure_ascii=False, indent=1))
    print(f"Wrote {len(PARITY_TEXTS)} reference vectors to {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
