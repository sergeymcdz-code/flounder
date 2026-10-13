#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
# NUL delimiters preserve spaces in configurable backup paths.
latest=''
while IFS= read -r -d '' file; do
  if [[ -z "$latest" || "$file" -nt "$latest" ]]; then latest="$file"; fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.sql.gz' \) -print0)
if [[ -z "$latest" ]]; then alert 'Flounder: no backup for weekly restore verification'; exit 1; fi
exec "$INFRA_DIR/restore.sh" "$latest"
