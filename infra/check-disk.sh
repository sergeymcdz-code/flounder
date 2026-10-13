#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
threshold="${DISK_THRESHOLD:-85}"
[[ "$threshold" =~ ^[0-9]+$ ]] && (( threshold <= 100 )) || exit 2
for path in / /home/deploy/backups /var/lib/docker; do
  [[ -e "$path" ]] || continue
  used="$(df -P "$path" | awk 'NR==2 {gsub(/%/, "", $5); print $5}')"
  if (( used > threshold )); then alert "Flounder: disk $path is ${used}% full (threshold ${threshold}%)"; fi
done
