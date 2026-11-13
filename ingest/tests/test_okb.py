import datetime as dt

from athena_ingest.okb import load_okb, parse_page


def test_loads_only_pages_inside_collections(okb_root):
    pages = load_okb(okb_root)
    ids = {p.id for p in pages}
    assert "README" not in ids                       # root files are not pages
    assert "general/policies/tuition-refunds" in ids  # nested folders are
    assert len(pages) == 7


def test_front_matter_and_citation_fields(okb_root):
    page = next(p for p in load_okb(okb_root) if p.id == "aice-beng/32-academic-probation")
    assert page.title == "Academic Probation"
    assert page.programme == "aice-beng"
    assert page.section_no == "9.2"
    assert page.updated_on == dt.date(2026, 8, 1)
    assert page.handbook == "B.Eng. in AiCE Student Handbook (SYNTHETIC-BEng-fixture.pdf)"
    assert page.applies_to == ["aice-beng"]
    assert not page.body.startswith("# ")             # the H1 title is stripped


def test_applies_to_rules(okb_root):
    pages = {p.id: p for p in load_okb(okb_root)}
    assert pages["general/policies/tuition-refunds"].applies_to == []       # university-wide
    assert pages["minutes/2026-08-20-gsc"].applies_to == ["ece-ms"]          # explicit list
    assert pages["minutes/2026-08-20-gsc"].handbook == "Committee minutes"
    assert pages["general/policies/tuition-refunds"].handbook.startswith("CMKL website (")


def test_page_without_front_matter_still_loads():
    page = parse_page("# Hello\n\nSome text.", "general/hello.md")
    assert page.title == "hello"
    assert page.programme == "general"
    assert page.body == "Some text."
