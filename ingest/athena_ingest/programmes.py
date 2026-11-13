"""Display names for programme codes. Unknown codes fall back to the code itself."""

PROGRAMMES: dict[str, tuple[str, str]] = {
    # code: (programme name, handbook name used in citations)
    "aice-beng": (
        "B.Eng. in Artificial Intelligence and Computer Engineering (AiCE)",
        "B.Eng. in AiCE Student Handbook",
    ),
    "aice-ms": (
        "M.S. in Artificial Intelligence and Computer Engineering (AiCE)",
        "M.S. in AiCE Student Handbook",
    ),
    "ece-ms": (
        "M.S. in Electrical and Computer Engineering (ECE)",
        "M.S. in ECE Student Handbook",
    ),
    "aii-ms": (
        "M.S. in Artificial Intelligence Innovation (Aii)",
        "M.S. in Aii Student Handbook",
    ),
    "general": (
        "CMKL University (applies to every programme)",
        "CMKL University policies and website",
    ),
}


def programme_name(code: str) -> str:
    return PROGRAMMES.get(code, (code, code))[0]


def handbook_name(code: str) -> str:
    return PROGRAMMES.get(code, (code, f"{code} handbook"))[1]
