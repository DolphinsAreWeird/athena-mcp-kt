from pathlib import Path

import pytest

FIXTURE_OKB = Path(__file__).resolve().parents[2] / "fixtures" / "okb-mini"


@pytest.fixture
def okb_root() -> Path:
    return FIXTURE_OKB
