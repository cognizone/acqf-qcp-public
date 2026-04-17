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
    {"country": "Angola", "model": "ao-nqf-levels"},
    {"country": "Botswana", "model": "bw-nqf-levels"},
    {"country": "Cabo Verde", "model": "cv-nqf-levels"},
    {"country": "Democratic Republic of the Congo", "placeholder": "Under development"},
    {"country": "Djibouti", "placeholder": "Under development"},
    {"country": "Eswatini", "model": "sz-nqf-levels"},
    {"country": "Ghana", "model": "gh-nqf-levels"},
    {"country": "Guinea-Bissau", "placeholder": "Under development"},
    {"country": "Kenya", "model": "ke-nqf-levels"},
    {"country": "Lesotho", "model": "ls-nqf-levels"},
    {"country": "Morocco", "model": "ma-nqf-levels"},
    {"country": "Mauritius", "model": "mu-nqf-levels"},
    {"country": "Mozambique", "model": "mz-nqf-levels"},
    {"country": "Namibia", "model": "na-nqf-levels"},
    {"country": "Seychelles", "model": "sc-nqf-levels"},
    {"country": "Senegal", "placeholder": "Under development"},
    {"country": "Sierra Leone", "placeholder": "Under development"},
    {"country": "Somalia", "placeholder": "Under development"},
    {"country": "South Africa", "model": "za-nqf-levels"},
    {"country": "South Sudan", "placeholder": "Under development"},
    {"country": "Southern African Development Community", "model": "sadc-qf-levels"},
    {"country": "Zambia", "model": "zm-nqf-levels"},
    {"country": "Zimbabwe", "model": "zw-nqf-levels"},
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
    for entry in ENTRIES:
        country_name = entry["country"]
        model_name = entry.get("model")
        placeholder = entry.get("placeholder")
        if model_name:
            title = display_title(read_title(model_name))
            lines.append(
                f'                                <li><b>{country_name}:</b> '
                f'<a href="model/{model_name}">{title}</a></li>'
            )
            continue
        if placeholder:
            lines.append(f"                                <li><b>{country_name}:</b> {placeholder}</li>")
            continue
        raise ValueError(f"Entry for {country_name} must define either 'model' or 'placeholder'")
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
