#!/usr/bin/env bash
# Install the pinned upstream CLI distribution; no application build is needed.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
destination=${1:-$root/build/tools}
mkdir -p "$destination"
destination=$(realpath -- "$destination")
read -r checksum archive < "$root/publication/jena.sha512"
distribution=${archive%.tar.gz}
if [[ ! -x $destination/$distribution/bin/arq ]]; then
  work=$(mktemp -d "$destination/download.XXXXXX")
  trap 'rm -rf -- "$work"' EXIT
  curl --fail --silent --show-error --location --connect-timeout 20 --max-time 180 \
    "https://archive.apache.org/dist/jena/binaries/$archive" --output "$work/$archive"
  (cd "$work" && sha512sum --check "$root/publication/jena.sha512")
  tar -xzf "$work/$archive" -C "$destination"
fi
"$destination/$distribution/bin/arq" --version
