"""Split a section into passages short enough for the embedding model.

paraphrase-multilingual-MiniLM-L12-v2 reads at most 128 tokens (~80-90 English words); anything
longer is silently cut off. So a section is split at its headings, then into paragraphs packed up
to MAX_WORDS, and an over-long paragraph is split at sentence ends. Each chunk remembers its
heading, and the embedded text is prefixed with the section title so a short chunk keeps its context.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

MAX_WORDS = 80
MIN_WORDS = 20
CHUNKER_VERSION = "v1-80w-unbatched"   # part of the content hash: changing chunking/embedding redoes every page

HEADING = re.compile(r"^(#{1,6})\s+(.*)$")
SENTENCE_END = re.compile(r"(?<=[.!?;:])\s+")


@dataclass
class Chunk:
    ord: int
    heading: str | None
    text: str


def chunk_section(body: str, max_words: int = MAX_WORDS, min_words: int = MIN_WORDS) -> list[Chunk]:
    blocks: list[tuple[str | None, list[str]]] = []      # (heading, paragraphs)
    heading: str | None = None
    paragraph: list[str] = []
    paragraphs: list[str] = []

    def end_paragraph():
        if paragraph:
            paragraphs.append(" ".join(line.strip() for line in paragraph).strip())
            paragraph.clear()

    for line in body.splitlines():
        m = HEADING.match(line.strip())
        if m:
            end_paragraph()
            if paragraphs:
                blocks.append((heading, paragraphs))
            paragraphs = []
            heading = m.group(2).strip()
        elif not line.strip():
            end_paragraph()
        else:
            paragraph.append(line)
    end_paragraph()
    if paragraphs:
        blocks.append((heading, paragraphs))

    chunks: list[Chunk] = []
    for block_heading, paras in blocks:
        pieces = [p for para in paras for p in _split_long(para, max_words)]
        current: list[str] = []
        for piece in pieces:
            if current and _words(" ".join(current + [piece])) > max_words:
                chunks.append(Chunk(len(chunks), block_heading, "\n".join(current)))
                current = []
            current.append(piece)
        if current:
            text = "\n".join(current)
            last = chunks[-1] if chunks else None
            if (last and last.heading == block_heading and _words(text) < min_words
                    and _words(last.text + " " + text) <= max_words * 1.25):
                last.text += "\n" + text          # fold a short tail into the previous chunk
            else:
                chunks.append(Chunk(len(chunks), block_heading, text))
    return [c for c in chunks if c.text.strip()]


def embedding_text(title: str, chunk: Chunk) -> str:
    parts = [title] + ([chunk.heading] if chunk.heading and chunk.heading != title else []) + [chunk.text]
    return ". ".join(parts)


def _split_long(paragraph: str, max_words: int) -> list[str]:
    if _words(paragraph) <= max_words:
        return [paragraph]
    out, current = [], []
    for sentence in SENTENCE_END.split(paragraph):
        words = sentence.split()
        while len(words) > max_words:                 # a single run-on "sentence": hard split
            if current:
                out.append(" ".join(current))
                current = []
            out.append(" ".join(words[:max_words]))
            words = words[max_words:]
        if current and len(current) + len(words) > max_words:
            out.append(" ".join(current))
            current = []
        current.extend(words)
    if current:
        out.append(" ".join(current))
    return out


def _words(text: str) -> int:
    return len(text.split())
