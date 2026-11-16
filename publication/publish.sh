#!/usr/bin/env bash
# Shared ACQF/ETF publisher: reviewed local RDF inputs only.
set -euo pipefail
common=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
mode=${1:-check}
selection=${2:-all}
case "$mode" in build|check) ;; *) echo "Usage: $0 {build|check} [all|acqf|etf]" >&2; exit 2 ;; esac
if [[ $selection == all ]]; then
  for config in "$common"/products/*/publication.json; do
    bash "${BASH_SOURCE[0]}" "$mode" "$(basename "$(dirname "$config")")"
  done
  exit
fi
[[ $selection =~ ^[a-z0-9-]+$ ]] || { echo "Invalid product: $selection" >&2; exit 2; }
for tool in arq riot rdfcompare shacl jq awk realpath; do command -v "$tool" >/dev/null; done
read -r jena_checksum jena_archive < "$common/publication/jena.sha512"
jena_version=${jena_archive#apache-jena-}
jena_version=${jena_version%.tar.gz}
[[ $(arq --version) == "Apache Jena version $jena_version" ]] || { echo "Use Apache Jena $jena_version" >&2; exit 1; }
queries=$common/publication/queries
config=$common/products/$selection/publication.json
[[ -f $config ]] || { echo "Unknown product: $selection" >&2; exit 2; }
product=$(dirname "$config")
mkdir -p "$common/build/publication/$selection"
work=$(mktemp -d "$common/build/publication/$selection/run.XXXXXX")
trap 'rm -rf -- "$work"' EXIT

# Resolve only regular files inside their declared checkout, including symlinks.
local_file() {
  local path
  path=$(realpath -e -- "$1/$2") || return
  [[ $path == "$1/"* && -f $path ]] || { echo "Invalid local input: $path" >&2; return 1; }
  printf '%s\n' "$path"
}
file_uri() { jq -nr --arg path "$1" '"file://" + ($path | split("/") | map(@uri) | join("/"))'; }

# Stage extensionless Turtle with an explicit base; each file stays a separate graph.
# This avoids mixing vocabulary sources or leaking their metadata into the shapes.
dataset=(--data="$work/manifest.ttl")
printf '@prefix p: <urn:publication:graph:> .\n' > "$work/manifest.ttl"
index=0
stage() {
  local id=$1 source=$2 target uri
  target=$work/input-$index.ttl
  index=$((index + 1))
  uri=$(file_uri "$source")
  if [[ $source == *.jsonld ]]; then
    riot --quiet --syntax=JSONLD11 --base="$uri" --formatted=TURTLE "$source" > "$target"
  else
    printf '@base <%s> .\n' "$uri" > "$target"
    cat -- "$source" >> "$target"
  fi
  printf '<%s> p:source <%s> .\n' "$id" "$(file_uri "$target")" >> "$work/manifest.ttl"
  dataset+=(--namedGraph="$target")
}
input=$(local_file "$common" common/model/exchange-model.ttl)
stage urn:publication:graph:common "$input"
relative=$(jq -er '.profile' "$config")
input=$(local_file "$product" "$relative")
stage urn:publication:graph:profile "$input"
# Curated RDF directories are the inventory. Only bound schemes contribute values.
for directory in "$common/common/vocabularies" "$common/common/validation-inputs" "$product/vocabularies"; do
  [[ -d $directory ]] || { echo "Missing vocabulary directory: $directory" >&2; exit 1; }
  while IFS= read -r -d '' file; do
    input=$(local_file "$common" "${file#"$common/"}")
    stage urn:publication:graph:vocabulary "$input"
  done < <(find "$directory" -type f -print0 | sort -z)
done
arq "${dataset[@]}" --query="$queries/bindings.rq" --results=TURTLE > "$work/bindings.ttl"
printf '<urn:publication:graph:bindings> p:source <%s> .\n' "$(file_uri "$work/bindings.ttl")" >> "$work/manifest.ttl"
dataset+=(--namedGraph="$work/bindings.ttl")
arq "${dataset[@]}" --query="$queries/input-errors.rq" --results=JSON > "$work/errors.json"
if ! jq -e '.results.bindings | length == 0' "$work/errors.json" >/dev/null; then
  jq -r '.results.bindings[] | [.error.value, .item.value] | @tsv' "$work/errors.json" >&2
  exit 1
fi
arq "${dataset[@]}" --query="$queries/allowed-values.rq" --results=TSV > "$work/members.tsv"
# TSV contains complete RDF IRI terms, already checked and sorted by SPARQL.
awk '
  NR == 1 { next }
  $1 != shape {
    if (shape != "") print ") ."
    shape = $1
    print shape " <http://www.w3.org/ns/shacl#in> ("
  }
  { print "  " $2 }
  END { if (shape != "") print ") ." }
' "$work/members.tsv" > "$work/lists.ttl"
arq "${dataset[@]}" --query="$queries/export-structure.rq" --results=TURTLE > "$work/structure.ttl"
# Parse the files together; never merge RDF by concatenating Turtle source text.
riot --quiet --formatted=TURTLE "$work/structure.ttl" "$work/lists.ttl" > "$work/generated.ttl"
shacl parse "$work/generated.ttl" > /dev/null
destination=$(realpath -m -- "${SITE_OUTPUT:-$common/docs}")
relative=$(jq -er '.output | select(startswith("docs/"))' "$config")
output=$(realpath -m -- "$destination/${relative#docs/}")
[[ $output == "$destination/"* ]] || { echo "Output must stay inside the site: $output" >&2; exit 1; }
if [[ $mode == check ]]; then
  rdfcompare "$work/generated.ttl" "$output" TURTLE TURTLE
  bash "$common/publication/tests/check.sh" "$common" "$config" "$work"
  echo "Checked $output"
else
  # Stage beside the destination so the final rename is atomic.
  mkdir -p -- "$(dirname "$output")"
  target=$(mktemp "$output.XXXXXX")
  trap 'rm -rf -- "$work"; rm -f -- "$target"' EXIT
  printf '# Generated by publication/publish.sh build; edit model, profile or vocabulary sources.\n' > "$target"
  cat "$work/generated.ttl" >> "$target"
  chmod 644 "$target"
  mv -f -- "$target" "$output"
  echo "Generated $output"
fi
