#!/usr/bin/env bash
# References are generated from assembled models by the shared build.
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./gradlew buildValidation
