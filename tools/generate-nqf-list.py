#!/usr/bin/env python3

from __future__ import annotations

import re
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parent
ROOT = TOOLS_DIR.parent
INDEX_HTML = ROOT / "docs" / "index.html"

BEGIN_MARKER = "<!-- BEGIN GENERATED NQF LEVEL LIST -->"
END_MARKER = "<!-- END GENERATED NQF LEVEL LIST -->"

TITLE_PATTERN = re.compile(r'dct:title\s+"([^"]+)"@[a-z]+')

ENTRIES = [
    ("Botswana", "bw-nqf-levels"),
    ("Cabo Verde", "cv-nqf-levels"),
    ("Kenya", "ke-nqf-levels"),
    ("Lesotho", "ls-nqf-levels"),
    ("Morocco", "ma-nqf-levels"),
    ("Mauritius", "mu-nqf-levels"),
    ("Mozambique", "mz-nqf-levels"),
    ("Southern African Development Community", "sadc-qf-levels"),
    ("Eswatini", "sz-nqf-levels"),
    ("Zimbabwe", "zw-nqf-levels"),
]


def read_title(model_name: str) -> str:
    model_path = ROOT / "docs" / "model" / model_name
    match = TITLE_PATTERN.search(model_path.read_text(encoding="utf-8"))
    if not match:
        raise ValueError(f"Could not find dct:title in {model_path}")
    return match.group(1)


def display_title(raw_title: str) -> str:
    title = raw_title.removesuffix(" levels and level descriptors")
    if title == "NQF":
        return "NQF levels"
    if "levels" in title.lower():
        return title
    return f"{title} levels"


def render_items() -> str:
    lines = []
    for country_name, model_name in ENTRIES:
        title = display_title(read_title(model_name))
        lines.append(
            f'                                <li><b>{country_name}:</b> '
            f'<a href="model/{model_name}">{title}</a></li>'
        )
    return "\n".join(lines)


def update_index() -> None:
    content = INDEX_HTML.read_text(encoding="utf-8")
    pattern = re.compile(
        rf"(?P<indent>\s*){re.escape(BEGIN_MARKER)}\n.*?\n(?P=indent){re.escape(END_MARKER)}",
        re.DOTALL,
    )

    replacement = (
        "                                "
        f"{BEGIN_MARKER}\n"
        f"{render_items()}\n"
        "                                "
        f"{END_MARKER}"
    )

    updated_content, count = pattern.subn(replacement, content)
    if count != 1:
        raise ValueError("Could not locate generated NQF list block in docs/index.html")

    INDEX_HTML.write_text(updated_content, encoding="utf-8")


if __name__ == "__main__":
    update_index()
