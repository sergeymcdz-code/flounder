#!/usr/bin/env python3
"""Rebuild D02 font subsets from the full, licensed WOFF2 fonts in D01."""
import argparse
import io
import pathlib
import subprocess
from fontTools import subset
from fontTools.ttLib import TTFont

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-ref', default='e76f3a853aecdbb8db8b880d0c5d25f31238b95a')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
font_dir = 'sport-platform/frontend/public/fonts'
coverage = 'U+0000-00FF,U+0400-04FF,U+2000-206F,U+20BD,U+2116,U+2190-21FF,U+2212'
size = 0
for name in ('golos-variable.woff2', 'oswald-variable.woff2'):
    source = subprocess.check_output(['git', 'show', f'{args.source_ref}:{font_dir}/{name}'], cwd=root)
    font = TTFont(io.BytesIO(source), recalcTimestamp=False)
    options = subset.Options()
    options.layout_features = ['*']
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=subset.parse_unicodes(coverage))
    subsetter.subset(font)
    font.flavor = 'woff2'
    destination = root / font_dir / name
    font.save(destination)
    size += destination.stat().st_size
    print(f'{name}: {destination.stat().st_size} bytes')
if size > 120_000:
    raise SystemExit(f'Font budget exceeded: {size} > 120000 bytes')
print(f'Total: {size} bytes')
