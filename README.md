# ACQF QCP Documentation

This repository hosts the website for ACQF QCP hosted at https://data.acqf-qcp.africa.

## Data Model
In order to generate the SHACL documentation using SHACL Play using the Ontology terms (and not in SHACL terms) the following can be done:

### Usage
In order to generate the SHACL documentation using SHACL Play using the Ontology terms (and not in SHACL terms) the following can be done:
1. Download Jena binary distribution and unzip it to `$JENA_HOME` and update the variable in `generate-documentation.sh` accordingly.
2. Generate diagram and HTML documentation (it downloads ELM and transforms the SHACL through `queries/generate-shacl-for-shacl-play.rq`) 
```bash
export JENA_HOME=~/soft/apache-jena-5.2.0/
export JENA_SPARQL=$JENA_HOME/bin/sparql
export JENA_RIOT=$JENA_HOME/bin/riot
./generate-documentation.sh
```