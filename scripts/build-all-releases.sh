#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== Building and Packaging opensearch-analysis-thaibreak ==="
cd "${ROOT_DIR}"

./gradlew check packageAllDistributions

DIST_OUT="${ROOT_DIR}/dist"
rm -rf "${DIST_OUT}"
mkdir -p "${DIST_OUT}"

echo "=== Generating SHA512 Checksums ==="
for zip in build/distributions/analysis-thaibreak-*.zip; do
    fname=$(basename "${zip}")
    cp "${zip}" "${DIST_OUT}/${fname}"
    cd "${DIST_OUT}"
    sha512sum "${fname}" > "${fname}.sha512"
    cd "${ROOT_DIR}"
    echo "  -> ${fname} + .sha512"
done

echo ""
echo "=== All Release Artifacts Ready in ${DIST_OUT} ==="
ls -lh "${DIST_OUT}"

echo ""
echo "To publish a release to GitHub using 'gh' CLI:"
echo "  gh release create v1.0.0 ${DIST_OUT}/* --title 'Release v1.0.0' --notes 'Initial production release of opensearch-analysis-thaibreak'"
