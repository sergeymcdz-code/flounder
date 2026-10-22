#!/usr/bin/env python3
"""Check every executable inline script against the committed Caddy policy."""
import base64
import hashlib
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
policy = (root / 'infra/Caddyfile').read_text()
paths = [root / 'sport-platform/frontend/index.html'] + [Path(p) for p in sys.argv[1:]]
for path in paths:
    scripts = [body for attrs, body in re.findall(r'<script\b([^>]*)>(.*?)</script>', path.read_text(), re.S | re.I)
               if not re.search(r'\bsrc\s*=', attrs, re.I)]
    assert len(scripts) == 1, f'{path}: изменился набор inline-скриптов; пересмотрите CSP'
    for script in scripts:
        digest = base64.b64encode(hashlib.sha256(script.encode()).digest()).decode()
        assert f"'sha256-{digest}'" in policy, f'{path}: обновите хеш CSP после проверки скрипта темы'
    print(f'CSP: хеш проверен ({path.name})')
