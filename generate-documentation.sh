#!/bin/bash

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

mkdir -p "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/shacl-play

$CURL -L -H "Accept: application/rdf+xml" http://data.europa.eu/snb/model/elm/ -o "$ELM_FILE"
$JENA_SPARQL --query ./queries/generate-shacl-for-shacl-play.rq --data "$ELM_FILE" --data "$INPUT_FILE" > "$SHACL_PLAY_FILE"

SHACLPLAY=shaclplay.jar
if ! [ -e "$SHACLPLAY" ]; then
  curl -L -o $SHACLPLAY https://github.com/sparna-git/shacl-play/releases/download/0.10.2/shacl-play-app-0.10.2-onejar.jar
fi
java -jar $SHACLPLAY doc -nsd -i "$SHACL_PLAY_FILE" -l en -o "$DOCUMENTATION_DESTINATION"

SHACLVIZ=shaclviz.jar
if ! [ -e "$SHACLVIZ" ]; then
    curl -L -o $SHACLVIZ https://repo1.maven.org/maven2/zone/cogni/semanticz/semanticz-shaclviz/1.0.2/semanticz-shaclviz-1.0.2-executable.jar
fi
java -jar $SHACLVIZ file:docs/model/exchange-model.ttl $IMAGE_DESTINATION --fieldQuery=docs/exchange-model/fields-acqf.rq --outputFormat tgf