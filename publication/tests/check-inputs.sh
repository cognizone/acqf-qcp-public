#!/usr/bin/env bash
# Product-independent malformed-input fixture; run once per complete site check.
set -euo pipefail
common=$1 work=$2
tests=$common/publication/tests
arq --data="$tests/input-errors.trig" --query="$common/publication/queries/input-errors.rq" --results=JSON > "$work/input-errors.json"
jq -e '
  [.results.bindings[].error.value] | unique == [
    "Catalogue member must reference exactly one scheme IRI",
    "Expected named shape and scheme IRIs",
    "Invalid profile binding",
    "Missing scheme or empty membership: not a scheme IRI",
    "Missing scheme or empty membership: urn:test:input:empty",
    "Missing scheme or empty membership: urn:test:input:missing",
    "Scheme defined in multiple input files",
    "Shape already has sh:in",
    "Vocabulary member must be an IRI"
  ]
' "$work/input-errors.json" >/dev/null
echo "Passed shared malformed-input checks"
