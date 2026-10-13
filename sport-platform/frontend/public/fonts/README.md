# Self-hosted fonts

Golos Text (body, variable weight 400–900) and Oswald (headings, variable weight
200–700) include Cyrillic and Latin. Golos Text supplies tabular numerals for result times.
Full fonts are retained;
there are no runtime requests to Google Fonts. Both are distributed under the
SIL Open Font License 1.1; the original notices are alongside the binaries.

Sources downloaded on 2026-10-13 from the Google Fonts repository:

- https://github.com/google/fonts/blob/main/ofl/golostext/GolosText%5Bwght%5D.ttf
- https://github.com/google/fonts/blob/main/ofl/oswald/Oswald%5Bwght%5D.ttf

Converted losslessly to WOFF2 using fontTools (`TTFont`, `flavor = 'woff2'`) with
Brotli. The original axes, glyph coverage and layout features are preserved.
Vite PWA's existing `woff2` precache pattern also includes these local files.
7aea06d71ace3cf39850287d2e54e8048c4e379b4139b77c2e4297a7aaa602c3  public/fonts/golos-variable.woff2
69ba06d7289f98d67249611c9d82d53299b6723c00fd82bb428ec6b5b714d73f  public/fonts/oswald-variable.woff2
