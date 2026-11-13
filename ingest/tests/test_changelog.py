import subprocess

from athena_ingest.changelog import parse_git_log, parse_subject, read_changelog

RS, US = "\x1e", "\x1f"


def test_parse_subject():
    assert parse_subject("Approve: Suspension (aice-beng/33-suspension.md)") == (
        "Approve", "Suspension", "aice-beng/33-suspension.md")
    assert parse_subject("Add new page: Graduate Studies Committee · 23 September 2026 (minutes/x.md)")[0] == "Add new page"
    assert parse_subject("Athena: simpler minutes review, fixes") is None


def test_parse_git_log_one_row_per_page():
    out = (f"{RS}abcdef1234{US}2026-10-01T10:00:00+07:00{US}KP{US}Approve: Probation (aice-beng/32-p.md)\n\n"
           f"M\taice-beng/32-p.md\n"
           f"{RS}1234567890{US}2026-09-23T09:00:00+07:00{US}Team{US}Build everything\n\n"
           f"A\taice-beng/32-p.md\nA\tREADME.md\nA\tgeneral/x.md\n")
    rows = parse_git_log(out)
    assert [(r.commit, r.action, r.title, r.path, r.status) for r in rows] == [
        ("abcdef1", "Approve", "Probation", "aice-beng/32-p.md", "M"),
        ("1234567", "Bulk edit", None, "aice-beng/32-p.md", "A"),
        ("1234567", "Bulk edit", None, "general/x.md", "A"),
    ]
    assert rows[0].section_id == "aice-beng/32-p"


def test_reads_a_real_git_history(tmp_path):
    okb = tmp_path / "okb"
    (okb / "aice-beng").mkdir(parents=True)
    page = okb / "aice-beng" / "10-fees.md"
    page.write_text("---\ntitle: Fees\n---\nOld.")

    def git(*args):
        subprocess.run(["git", "-C", str(tmp_path), *args], check=True, capture_output=True,
                       env={"GIT_AUTHOR_NAME": "Athena", "GIT_AUTHOR_EMAIL": "a@example.invalid",
                            "GIT_COMMITTER_NAME": "Athena", "GIT_COMMITTER_EMAIL": "a@example.invalid",
                            "PATH": "/usr/bin:/bin:/opt/homebrew/bin"})

    git("init", "-q")
    git("add", ".")
    git("commit", "-q", "-m", "Add new page: Fees (aice-beng/10-fees.md)")
    page.write_text("---\ntitle: Fees\n---\nNew.")
    git("commit", "-q", "-am", "Approve: Fees (aice-beng/10-fees.md)")

    rows = read_changelog(okb)
    assert [(r.action, r.status, r.path) for r in rows] == [
        ("Approve", "M", "aice-beng/10-fees.md"),
        ("Add new page", "A", "aice-beng/10-fees.md"),
    ]


def test_no_git_repo_means_no_changes(tmp_path):
    assert read_changelog(tmp_path) == []
