#!/usr/bin/env bash
set -euo pipefail

# Fetch the exact upstream Aniyomi source tree for the host-runtime integration spike.
# This intentionally does not rewrite AnimeWave's settings.gradle.kts: Aniyomi is a
# multi-module application with its own build logic/catalogs, so blindly including
# its app modules in this project would produce a broken or misleading integration.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENDOR_DIR="${ROOT}/vendor/aniyomi"
VERSION="v0.18.2.1"
REPOSITORY="https://github.com/aniyomiorg/aniyomi.git"

if [[ -e "${VENDOR_DIR}" ]]; then
  echo "Refusing to overwrite existing ${VENDOR_DIR}. Move it aside or remove it first." >&2
  exit 2
fi

mkdir -p "$(dirname "${VENDOR_DIR}")"
git clone --filter=blob:none --no-checkout --branch "${VERSION}" "${REPOSITORY}" "${VENDOR_DIR}"
git -C "${VENDOR_DIR}" checkout --detach "${VERSION}"

for required in settings.gradle.kts app source-api core/common domain data gradle; do
  if [[ ! -e "${VENDOR_DIR}/${required}" ]]; then
    echo "Pinned Aniyomi checkout is missing required path: ${required}" >&2
    exit 3
  fi
done

actual="$(git -C "${VENDOR_DIR}" describe --tags --exact-match HEAD)"
if [[ "${actual}" != "${VERSION}" ]]; then
  echo "Expected ${VERSION}, got ${actual}" >&2
  exit 4
fi

echo "Fetched Aniyomi ${VERSION} into ${VENDOR_DIR}"
echo "Next: port/integrate the required host modules and build logic; this checkout alone is not runtime integration."
