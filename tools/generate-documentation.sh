#!/bin/bash

set -euo pipefail

TOOLS_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$TOOLS_DIR/.." && pwd)

export JAVA_HOME=~/.jdks/corretto-17.0.11

# SHACL file to generate documentation for
INPUT_FILE="docs/model/exchange-model.ttl"
BUILD_DIR=${BUILD_DIR:=./build}
CURL=${CURL:=curl}
JENA_SPARQL=${JENA_SPARQL:=sparql}
JENA_RIOT=${JENA_RIOT:=riot}
SHACL_PLAY=${SHACL_PLAY:=shaclplay}

INPUT_FILE_WITHOUT_EXTENSION="${INPUT_FILE%.*}" # ./exchange-model.ttl
SHACL_PLAY_FILE="$BUILD_DIR/input-for-shacl-play.ttl"
ELM_FILE="$BUILD_DIR/elm.rdf"
DOCUMENTATION_DESTINATION="docs/exchange-model/exchange-model.html"
IMAGE_DESTINATION="docs/exchange-model/exchange-model.tgf"

mkdir -p "$ROOT_DIR/$BUILD_DIR"
mkdir -p "$ROOT_DIR/$BUILD_DIR/shacl-play"

$CURL -L https://op.europa.eu/o/opportal-service/euvoc-download-handler?cellarURI=http%3A%2F%2Fpublications.europa.eu%2Fresource%2Fdistribution%2Fsnb-model%2F20250514-0%2Frdf%2Fowl%2FELM.rdf -o "$ROOT_DIR/$ELM_FILE"
$JENA_SPARQL --query "$ROOT_DIR/queries/generate-shacl-for-shacl-play.rq" --data "$ROOT_DIR/$ELM_FILE" --data "$ROOT_DIR/$INPUT_FILE" > "$ROOT_DIR/$SHACL_PLAY_FILE"

SHACLPLAY="$ROOT_DIR/shaclplay.jar"
if ! [ -e "$SHACLPLAY" ]; then
  curl -L -o "$SHACLPLAY" https://github.com/sparna-git/shacl-play/releases/download/0.11.2/shacl-play-app-0.11.2-onejar.jar
fi
java -jar "$SHACLPLAY" doc -nsd -i "$ROOT_DIR/$SHACL_PLAY_FILE" -l en -o "$ROOT_DIR/$DOCUMENTATION_DESTINATION"

SHACLVIZ="$ROOT_DIR/shaclviz.jar"
if ! [ -e "$SHACLVIZ" ]; then
    curl -L -o "$SHACLVIZ" https://repo1.maven.org/maven2/zone/cogni/semanticz/semanticz-shaclviz/1.0.2/semanticz-shaclviz-1.0.2-executable.jar
fi
java -jar "$SHACLVIZ" "file:$ROOT_DIR/docs/model/exchange-model.ttl" "$ROOT_DIR/$IMAGE_DESTINATION" --fieldQuery="$ROOT_DIR/docs/exchange-model/fields-acqf.rq" --outputFormat tgf
