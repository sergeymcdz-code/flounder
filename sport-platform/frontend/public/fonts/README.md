# Self-hosted fonts

Golos Text (body, variable weight 400–900) and Oswald (headings, variable weight
200–700) include Cyrillic and Latin. Golos Text supplies tabular numerals for result times.
D02 subsets retain Latin, Cyrillic, digits, punctuation, the ruble sign and №;
there are no runtime requests to Google Fonts. Both are distributed under the
SIL Open Font License 1.1; the original notices are alongside the binaries.

Sources downloaded on 2026-10-13 from the Google Fonts repository:

- https://github.com/google/fonts/blob/main/ofl/golostext/GolosText%5Bwght%5D.ttf
- https://github.com/google/fonts/blob/main/ofl/oswald/Oswald%5Bwght%5D.ttf

Converted losslessly to WOFF2 using fontTools (`TTFont`, `flavor = 'woff2'`) with
Brotli. The original axes and layout features are preserved; D02 limits glyph coverage
to the subset below.
Vite PWA's existing `woff2` precache pattern also includes these local files.

## D02 subsets

Reproduce from the original D01 WOFF2 files with Python, fontTools 4.61.1 and
Brotli 1.2.0 (SIL OFL notices remain unchanged):

```sh
python scripts/subset-d02-fonts.py
```

The source commit is pinned in the script; `--source-ref` can select another
commit containing the original full files. Coverage: `U+0000-00FF`,
`U+0400-04FF`, `U+2000-206F`, `U+20BD`, `U+2116`, `U+2190-21FF`, `U+2212`.
All available layout features and variable weight axes are retained.
`font-display: swap` and the filenames/PWA precache pattern remain unchanged.

Total: **106,540 bytes** (Golos Text 60,076; Oswald 46,464), under 120,000 bytes.

```text
7a0f55ce913fa87e0c1876d102cb100cc6b43e8cc3adf1dccfa5c21292741ad7  golos-variable.woff2
220cc869e65db567db4e7145eefcfe8a72ffe310ce10506342ef57ebda51460e  oswald-variable.woff2
```
