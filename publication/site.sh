#!/usr/bin/env bash
# Assemble the website from maintained sources; docs/ contains delivery artifacts only.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
mode=${1:-check}
case "$mode" in build|check) ;; *) echo "Usage: $0 {build|check}" >&2; exit 2 ;; esac
mkdir -p "$root/build/site"
work=$(mktemp -d "$root/build/site/run.XXXXXX")
trap 'rm -rf -- "$work"' EXIT
stage=$work/docs
mkdir -p "$stage"
configs=("$root/publication/site.json" "$root"/products/*/publication.json)
for config in "${configs[@]}"; do
  while IFS= read -r mapping; do
    relative=$(jq -er '.source' <<< "$mapping")
    source=$(realpath -e -- "$root/$relative")
    [[ $source == "$root/"* ]] || { echo "Source must stay in repository: $source" >&2; exit 1; }
    target=$(jq -r '.target' <<< "$mapping")
    while IFS= read -r -d '' file; do
      if [[ -d $source ]]; then suffix=${file#"$source/"}; else suffix=""; fi
      if jq -e --arg file "$suffix" '(.exclude // []) | index($file) != null' <<< "$mapping" >/dev/null; then continue; fi
      output=$(realpath -m -- "$stage/$target${suffix:+/$suffix}")
      [[ $output == "$stage/"* && ! -e $output ]] || { echo "Invalid or duplicate site output: $output" >&2; exit 1; }
      mkdir -p -- "$(dirname "$output")"
      cp -- "$file" "$output"
    done < <(find "$source" -type f -print0)
  done < <(jq -c '.resources[]' "$config")
done
if [[ $mode == build ]]; then
  SITE_OUTPUT="$stage" bash "$root/publication/publish.sh" build
  # Copy declared outputs only; never delete unrelated local files from docs/.
  mkdir -p "$root/docs"
  cp -R "$stage/." "$root/docs/"
  echo "Built website in $root/docs"
else
  while IFS= read -r -d '' file; do
    relative=${file#"$stage/"}
    cmp -- "$file" "$root/docs/$relative" || { echo "Stale website file: docs/$relative" >&2; exit 1; }
  done < <(find "$stage" -type f -print0)
  for config in "$root"/products/*/publication.json; do
    contract=$root/$(jq -er '.tests' "$config")/published-paths.txt
    if [[ -f $contract ]]; then
      while IFS= read -r relative; do
        [[ $relative == docs/* && -f $root/$relative ]] || { echo "Missing established public path: $relative" >&2; exit 1; }
      done < "$contract"
    fi
  done
  bash "$root/publication/publish.sh" check
  # Reject obsolete tracked outputs, while leaving unrelated untracked local files alone.
  find "$stage" -type f -printf 'docs/%P\n' > "$work/expected"
  for config in "$root"/products/*/publication.json; do jq -r '.output' "$config" >> "$work/expected"; done
  sort -u "$work/expected" > "$work/expected-sorted"
  git -C "$root" ls-files docs | sort -u > "$work/tracked"
  if [[ -n $(comm -23 "$work/tracked" "$work/expected-sorted") ]]; then
    echo "Obsolete tracked site files:" >&2
    comm -23 "$work/tracked" "$work/expected-sorted" >&2
    exit 1
  fi
  echo "Checked website resources and both product contracts"
fi
