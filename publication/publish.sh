#!/usr/bin/env bash
# Build or check product validation models through Gradle.
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-check}" in
  build) task=buildModels ;;
  check) task=checkModels ;;
  *) echo "Usage: $0 {build|check} [product|all]" >&2; exit 2 ;;
esac
exec ./gradlew "$task" "-Pproduct=${2:-all}"
