#!/usr/bin/env bash
# Exercise branch selection and production-checkout isolation without deployment.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/prod/infra" "$TMP/prod/scripts/demo" "$TMP/prod/sport-platform/frontend" "$TMP/bin"
cp "$REPO/infra/deploy-staging.sh" "$REPO/infra/compose.staging.yaml" "$REPO/infra/Caddyfile.staging" "$TMP/prod/infra/"
git init -q --initial-branch=main "$TMP/prod"
git -C "$TMP/prod" config user.email test@example.invalid
git -C "$TMP/prod" config user.name 'M09 Test'
touch "$TMP/prod/scripts/demo/.gitkeep" "$TMP/prod/sport-platform/frontend/.gitkeep"
git -C "$TMP/prod" add .
git -C "$TMP/prod" commit -qm 'Test infrastructure'
prod_head="$(git -C "$TMP/prod" rev-parse HEAD)"
git -C "$TMP/prod" switch -qc feat/test
git -C "$TMP/prod" rm -q infra/compose.staging.yaml
git -C "$TMP/prod" commit -qm 'Feature without staging compose'
git -C "$TMP/prod" switch -q main
git clone -q --bare "$TMP/prod" "$TMP/origin.git"
git -C "$TMP/prod" remote add origin "$TMP/origin.git"
cat > "$TMP/staging.env" <<'ENV'
DOMAIN=staging.sportgate.pro
POSTGRES_DB=sport_platform_staging
ADMIN_EMAIL=test@example.invalid
ADMIN_PASSWORD=test-only-admin
DEMO_PASSWORD=test-only-demo
ENV
export STAGING_ENV="$TMP/staging.env" STAGING_DIR="$TMP/staging" TEST_LOG="$TMP/calls"
cat > "$TMP/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$TEST_LOG"
DOCKER
chmod +x "$TMP/bin/"*
export PATH="$TMP/bin:$PATH"
"$TMP/prod/infra/deploy-staging.sh" feat/test
"$TMP/prod/infra/deploy-staging.sh" feat/test
[[ "$(git -C "$TMP/prod" rev-parse HEAD)" == "$prod_head" ]]
[[ "$(git -C "$TMP/prod" branch --show-current)" == main ]]
grep -q -- "--project-directory $STAGING_DIR/infra -f $TMP/prod/infra/compose.staging.yaml" "$TEST_LOG"
seed_call="$(grep '^docker run ' "$TEST_LOG" | head -1)"
for expected in '--network host' "--user $(id -u):$(id -g)" '-e HOME=/tmp' \
  '-e API_URL=http://127.0.0.1:8081' '-e DEMO_SEED_ENABLED=true' '-e NODE_ENV=development' \
  '-e SPRING_PROFILES_ACTIVE=staging' '-e ADMIN_EMAIL' '-e ADMIN_PASSWORD' '-e DEMO_PASSWORD' \
  "-v $STAGING_DIR:/repo" '-w /repo/scripts/demo' 'node:22-alpine sh -c npm ci && npx tsx seed.ts'; do
  [[ "$seed_call" == *"$expected"* ]] || { echo "Missing seed argument: $expected"; exit 1; }
done
# A subdirectory of production must never be mistaken for a worktree.
export STAGING_DIR="$TMP/prod/infra"
if "$TMP/prod/infra/deploy-staging.sh" feat/test; then echo 'Expected worktree guard'; exit 1; fi
[[ "$(git -C "$TMP/prod" rev-parse HEAD)" == "$prod_head" ]]
export STAGING_DIR="$TMP/staging"
echo dirty > "$STAGING_DIR/untracked"
if "$TMP/prod/infra/deploy-staging.sh" feat/test; then echo 'Expected dirty tree guard'; exit 1; fi
if "$TMP/prod/infra/deploy-staging.sh" --bad-branch; then echo 'Expected invalid branch refusal'; exit 1; fi
echo 'Staging script tests passed (branch worktree, seed target, dirty tree and production isolation)'

bash "$REPO/infra/tests/harden-test.sh"
