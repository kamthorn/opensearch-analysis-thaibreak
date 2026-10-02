#!/usr/bin/env bash
# Installs analysis-thaibreak-<version>.0.zip into the official OpenSearch image of the
# same version, starts a single node, and checks that _analyze segments Thai text.
# Usage: scripts/smoke-test.sh <opensearch-version> [zip-dir]
set -euo pipefail

VERSION="${1:?usage: smoke-test.sh <opensearch-version> [zip-dir]}"
ZIP_DIR="${2:-build/distributions}"
ZIP="$(cd "$ZIP_DIR" && pwd)/analysis-thaibreak-${VERSION}.0.zip"
NAME="thaibreak-smoke-${VERSION//./-}"
PORT="${SMOKE_PORT:-19200}"

[ -f "$ZIP" ] || { echo "missing $ZIP" >&2; exit 2; }
trap 'docker logs "$NAME" 2>&1 | tail -40; docker rm -f "$NAME" >/dev/null 2>&1' EXIT

docker run -d --name "$NAME" \
  -e discovery.type=single-node -e DISABLE_SECURITY_PLUGIN=true -e DISABLE_INSTALL_DEMO_CONFIG=true \
  -v "$ZIP":/tmp/plugin.zip:ro -p "$PORT":9200 --entrypoint bash \
  "opensearchproject/opensearch:${VERSION}" \
  -c "/usr/share/opensearch/bin/opensearch-plugin install --batch file:///tmp/plugin.zip && exec /usr/share/opensearch/opensearch-docker-entrypoint.sh opensearch" >/dev/null

for _ in $(seq 1 100); do
  curl -sf "localhost:${PORT}" >/dev/null && break
  [ "$(docker inspect -f '{{.State.Running}}' "$NAME")" = true ] || { echo "container exited before startup" >&2; exit 1; }
  sleep 3
done
curl -sf "localhost:${PORT}" >/dev/null || { echo "node did not start" >&2; exit 1; }

RESULT="$(curl -sf -XPOST "localhost:${PORT}/_analyze" -H 'Content-Type: application/json' \
  -d '{"tokenizer":"thaibreak","text":"ภาษาไทยสนามบิน"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4 | paste -sd'|')"
echo "OpenSearch ${VERSION}: ${RESULT}"
[ "$RESULT" = "ภาษา|ไทย|สนามบิน" ] || { echo "unexpected tokens" >&2; exit 1; }
