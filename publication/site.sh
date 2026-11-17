#!/usr/bin/env bash
# Compatibility entry point; the website build lives in Gradle.
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-check}" in
  build) exec ./gradlew buildValidation ;;
  check) exec ./gradlew check ;;
  *) echo "Usage: $0 {build|check}" >&2; exit 2 ;;
esac
