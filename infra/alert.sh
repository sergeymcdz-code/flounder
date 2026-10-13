#!/usr/bin/env bash
# Send a plain text cron notification; never print the token or API response.
set -euo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
[[ $# -eq 1 ]] || { echo 'Usage: alert.sh "text"' >&2; exit 2; }
if [[ -z "${TELEGRAM_BOT_TOKEN:-}" || -z "${TELEGRAM_CHAT_ID:-}" ]]; then
  echo '[alert] Telegram is not configured' >&2
  exit 1
fi
# Pass the URL on stdin so the token does not appear in curl command arguments.
[[ "$TELEGRAM_BOT_TOKEN" =~ ^[0-9]+:[A-Za-z0-9_-]+$ ]] || exit 2
printf 'url = "https://api.telegram.org/bot%s/sendMessage"\n' "$TELEGRAM_BOT_TOKEN" |
  curl --config - --silent --fail --output /dev/null --connect-timeout 10 --max-time 30 \
    --data-urlencode "chat_id=$TELEGRAM_CHAT_ID" --data-urlencode "text=$1" || {
      echo '[alert] Telegram request failed' >&2; exit 1;
    }
