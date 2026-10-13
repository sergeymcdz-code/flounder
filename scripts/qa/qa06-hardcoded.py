"""Inventory literal colors and dimensions outside globals.css for D01."""
import csv
import re
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = root / 'sport-platform/frontend/src'
pattern = re.compile(
    r'#[0-9a-fA-F]{3,8}\b|(?:rgba?|hsla?)\([^)]*\)|'
    r'(?<![\w-])-?(?:\d*\.)?\d+(?:px|rem|em|pt|mm|vh|vw|ms|%)(?![\w-])|'
    r'(?<=:\s)(?:white|black|transparent)\b|'
    r'\b(?:size|width|height)=\{\d+\}'
)
rows = []
for path in sorted(source.rglob('*')):
    if path.suffix not in {'.css', '.tsx'} or path.name == 'globals.css':
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        values = pattern.findall(line)
        if values:
            rows.append((str(path.relative_to(root)), number, ' | '.join(values), line.strip()))
output = root / 'docs/qa/QA06-hardcoded-values.tsv'
with output.open('w') as handle:
    writer = csv.writer(handle, delimiter='\t', lineterminator='\n')
    writer.writerow(('file', 'line', 'values', 'source'))
    writer.writerows(rows)
for filename, count in Counter(row[0] for row in rows).items():
    print(f'{count}\t{filename}')
print(f'Total: {len(rows)} lines, {len(set(row[0] for row in rows))} files')
