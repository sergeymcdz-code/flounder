#!/usr/bin/env bash
# Deploy a remote branch in an independent worktree; never checkout production.
set -euo pipefail
[[ $# -eq 1 ]] || { echo 'Usage: deploy-staging.sh <remote-branch>'; exit 2; }
BRANCH="$1"
git check-ref-format --branch "$BRANCH" >/dev/null
[[ "$BRANCH" != -* ]] || exit 2
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGING_DIR="${STAGING_DIR:-/home/deploy/flounder-staging}"
STAGING_ENV="${STAGING_ENV:-/home/deploy/.config/flounder/.env.staging}"
[[ -r "$STAGING_ENV" ]] || { echo "Missing $STAGING_ENV"; exit 1; }
STAGING_ENV="$(realpath "$STAGING_ENV")"
set -a
# shellcheck disable=SC1090
source "$STAGING_ENV"
set +a
[[ "${DOMAIN:-}" == staging.sportgate.pro && "${POSTGRES_DB:-}" == sport_platform_staging ]] || {
  echo 'Staging domain/database guard failed'; exit 1;
}
: "${ADMIN_EMAIL:?required}" "${ADMIN_PASSWORD:?required}" "${DEMO_PASSWORD:?required}"
mkdir -p "$(dirname "$STAGING_DIR")"
exec 9>"$(dirname "$STAGING_DIR")/.flounder-staging.lock"
flock -n 9 || { echo 'Staging deployment already running'; exit 1; }
git -C "$REPO_DIR" fetch origin "refs/heads/$BRANCH"
COMMIT="$(git -C "$REPO_DIR" rev-parse FETCH_HEAD)"
if [[ -e "$STAGING_DIR" ]]; then
  [[ "$(realpath "$STAGING_DIR")" != "$REPO_DIR" ]] || exit 2
  [[ "$(git -C "$STAGING_DIR" rev-parse --show-toplevel)" == "$(realpath "$STAGING_DIR")" ]] || {
    echo 'STAGING_DIR must be an independent worktree root'; exit 2;
  }
  [[ "$(git -C "$STAGING_DIR" rev-parse --path-format=absolute --git-common-dir)" == "$(git -C "$REPO_DIR" rev-parse --path-format=absolute --git-common-dir)" ]] || {
    echo 'STAGING_DIR belongs to another repository'; exit 2;
  }
  [[ -z "$(git -C "$STAGING_DIR" status --porcelain)" ]] || { echo 'Staging tree is dirty'; exit 1; }
  git -C "$STAGING_DIR" checkout --detach "$COMMIT"
else
  git -C "$REPO_DIR" worktree add --detach "$STAGING_DIR" "$COMMIT"
fi
# Build the selected branch using reviewed infrastructure from the main checkout.
export STAGING_CADDYFILE="$REPO_DIR/infra/Caddyfile.staging"
compose() { docker compose --project-name flounder-staging --project-directory "$STAGING_DIR/infra" -f "$REPO_DIR/infra/compose.staging.yaml" --env-file "$STAGING_ENV" "$@"; }
compose config --quiet
compose up -d --build --wait --wait-timeout 300
# Run the HTTP seed in Docker; Node/npm are not required on the host.
docker run --rm --network host --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -e API_URL=http://127.0.0.1:8081 -e DEMO_SEED_ENABLED=true -e NODE_ENV=development \
  -e SPRING_PROFILES_ACTIVE=staging -e ADMIN_EMAIL -e ADMIN_PASSWORD -e DEMO_PASSWORD \
  -v "$STAGING_DIR":/repo -w /repo/scripts/demo node:22-alpine sh -c 'npm ci && npx tsx seed.ts'
printf 'Staging deployed: %s (%s)\n' "$BRANCH" "$COMMIT"
