#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
# NUL delimiters preserve spaces in configurable backup paths.
latest=''
while IFS= read -r -d '' file; do
  if [[ -z "$latest" || "$file" -nt "$latest" ]]; then latest="$file"; fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.sql.gz' -o -name '*.dump.age' \) -print0)
if [[ -z "$latest" ]]; then alert 'Flounder: нет копии для проверки восстановления'; exit 1; fi
if [[ "$latest" == *.age ]]; then
  # The private identity must never be stored on the production server.
  alert 'Flounder: новая копия зашифрована; владелец должен проверить восстановление на отдельном узле со своим ключом.'
  exit 0
fi
exec "$INFRA_DIR/restore.sh" "$latest"
