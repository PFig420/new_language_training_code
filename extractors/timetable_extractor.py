#!/usr/bin/env python3
"""
Bus Timetable PDF → CSV Extractor  (Python)
============================================
Dependency
    pip install pdfplumber

Usage
    python timetable_extractor.py <input.pdf> [output.csv]
"""

import csv
import re
import sys
from pathlib import Path

try:
    import pdfplumber
except ImportError:
    sys.exit("Missing dependency. Run:  pip install pdfplumber")

# Matches time tokens such as 7:40 or 10:00
_TIME_RE = re.compile(r"\b(\d{1,2}:\d{2})\b")


def parse_timetable(pdf_path: str) -> list[list[str]]:
    """
    Open a PDF and extract timetable rows.

    Each row is [stop_name, time_1, time_2, ...].
    Lines that contain no time tokens (headers, footers, legends) are skipped.
    """
    rows: list[list[str]] = []

    with pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
            text = page.extract_text(x_tolerance=3, y_tolerance=3)
            if not text:
                continue

            for raw_line in text.splitlines():
                line = raw_line.strip()
                times: list[str] = []
                first_start: int | None = None

                for m in _TIME_RE.finditer(line):
                    if first_start is None:
                        first_start = m.start()
                    times.append(m.group())

                if not times or first_start is None:
                    continue

                stop_name = line[:first_start].strip()
                if stop_name:
                    rows.append([stop_name] + times)

    return rows


def write_csv(rows: list[list[str]], csv_path: str) -> None:
    """Write extracted rows to a UTF-8 CSV file with a generated header row."""
    if not rows:
        sys.exit("No timetable data found in the PDF.")

    max_services = max(len(r) - 1 for r in rows)
    header = ["Paragem"] + [f"Serviço_{i}" for i in range(1, max_services + 1)]

    # utf-8-sig adds a BOM so Excel opens the file correctly
    with open(csv_path, "w", newline="", encoding="utf-8-sig") as fh:
        writer = csv.writer(fh)
        writer.writerow(header)
        writer.writerows(rows)

    print(f"✓ {len(rows)} stops written to '{csv_path}'")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("Usage: python timetable_extractor.py <input.pdf> [output.csv]")

    pdf_in  = sys.argv[1]
    csv_out = sys.argv[2] if len(sys.argv) > 2 else str(Path(pdf_in).with_suffix(".csv"))
    write_csv(parse_timetable(pdf_in), csv_out)
