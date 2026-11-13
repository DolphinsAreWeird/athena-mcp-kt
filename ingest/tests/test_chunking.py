from athena_ingest.chunking import Chunk, chunk_section, embedding_text


def words(n, word="word"):
    return " ".join(f"{word}{i}." for i in range(n))


def test_short_section_is_one_chunk():
    chunks = chunk_section("First paragraph.\n\nSecond paragraph.")
    assert len(chunks) == 1
    assert chunks[0].text == "First paragraph.\nSecond paragraph."


def test_headings_start_new_chunks_and_are_remembered():
    body = "Intro text here.\n\n## Fees\n\nThe fee is 100.\n\n## Refunds\n\nNo refunds."
    chunks = chunk_section(body)
    assert [c.heading for c in chunks] == [None, "Fees", "Refunds"]
    assert [c.ord for c in chunks] == [0, 1, 2]


def test_long_paragraph_is_split_under_the_limit():
    chunks = chunk_section(words(300), max_words=80)
    assert len(chunks) >= 4
    assert all(len(c.text.split()) <= 80 * 1.25 for c in chunks)
    assert " ".join(c.text for c in chunks).split() == words(300).split()   # nothing lost


def test_short_tail_is_folded_into_previous_chunk():
    body = words(70) + "\n\n" + "Tail."
    chunks = chunk_section(body, max_words=72, min_words=20)
    assert len(chunks) == 1


def test_embedding_text_prefixes_title_and_heading():
    assert embedding_text("Fees", Chunk(0, "Late payment", "Pay on time.")) == "Fees. Late payment. Pay on time."
    assert embedding_text("Fees", Chunk(0, None, "Pay on time.")) == "Fees. Pay on time."
