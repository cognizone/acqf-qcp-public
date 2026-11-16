#!/usr/bin/env bash
# Regression fixtures run against the standalone export with Jena's SHACL CLI.
set -euo pipefail
common=$1 config=$2 work=$3
tests=$common/publication/tests
model=$work/generated.ttl
expectations=$(dirname "$config")/$(jq -er '.tests' "$config")
arq --data="$model" --query="$tests/contract.rq" --results=JSON > "$work/contract.json"
jq -e --slurpfile expected "$expectations/contract.json" '
  .results.bindings | length == 1 and (.[0] |
    .count.value == ($expected[0].levels | tostring) and
    .prefixCount.value == ($expected[0].prefixes | tostring))
' "$work/contract.json" >/dev/null
arq --data="$model" --query="$tests/level-shapes.rq" --results=TURTLE > "$work/level-shapes.ttl"
arq --data="$model" --query="$tests/level-data.rq" --results=TURTLE > "$work/level-values.ttl"
first=$(jq -er '.results.bindings[0].first.value' "$work/contract.json")
cp "$tests/levels.ttl" "$work/levels.ttl"
printf '<urn:test:case:mixed> <https://data.acqf-qcp.africa/model/elm-extension/educationLevelNqf> %s .\n' "<$first>" >> "$work/levels.ttl"
riot --quiet --formatted=TURTLE "$work/levels.ttl" "$work/level-values.ttl" "$expectations/cross-level.ttl" > "$work/level-data.ttl"
# Read the RDF report explicitly; do not depend on the CLI's conformance exit code.
shacl validate --shapes "$work/level-shapes.ttl" --data "$work/level-data.ttl" > "$work/level-report.ttl"
arq --data="$work/level-report.ttl" --query="$tests/level-report.rq" --results=TSV > "$work/level-actual.tsv"
diff -u "$expectations/level-expected.tsv" "$work/level-actual.tsv"
arq --data="$model" --query="$tests/notation-shapes.rq" --results=TURTLE > "$work/notation-shapes.ttl"
shacl validate --shapes "$work/notation-shapes.ttl" --data "$tests/notation.ttl" > "$work/notation-report.ttl"
arq --data="$work/notation-report.ttl" --query="$tests/notation-report.rq" --results=TSV > "$work/notation-actual.tsv"
printf '?focus\n<urn:test:case:duplicate1>\n<urn:test:case:duplicate2>\n<urn:test:case:numeric1>\n<urn:test:case:numeric2>\n' > "$work/notation-expected.tsv"
diff -u "$work/notation-expected.tsv" "$work/notation-actual.tsv"
# Optional historical contract belongs to the product, not the shared runner.
baseline=$(jq -r '.baseline // empty' "$expectations/contract.json")
if [[ -n $baseline ]]; then
  git -C "$common" show "$baseline:$(jq -er '.output' "$config")" > "$work/baseline.ttl"
  rdfcompare "$model" "$work/baseline.ttl" TURTLE TURTLE
fi
echo "Passed level, cardinality, diagnostic and notation checks"
