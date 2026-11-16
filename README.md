# ACQF QCP Documentation

This repository hosts the website for ACQF QCP hosted at https://data.acqf-qcp.africa.

## Data Model

Generate SHACL documentation with ontology labels using SHACL Play:

1. Run `bash publication/setup-jena.sh` to install the checksum-pinned Jena CLI.
2. Generate NQF list to the documentation
```bash
python "tools/generate-nqf-list.py"
```
3. Generate diagram and HTML documentation (it downloads ELM and transforms the SHACL through `queries/generate-shacl-for-shacl-play.rq`) 
```bash
export JENA_HOME="$PWD/build/tools/apache-jena-6.2.0"
export JENA_SPARQL=$JENA_HOME/bin/sparql
export JENA_RIOT=$JENA_HOME/bin/riot
./tools/generate-documentation.sh
```

## Product validation (dev)

The existing commands remain available:

```bash
./gradlew buildValidation
./gradlew check
```


See [publication/README.md](publication/README.md) for repository ownership, the
architecture diagram, product profiles, standalone exports and CI checks. Edit the
common model and ACQF profile, then regenerate
`docs/model/exchange-model.ttl`; do not edit the generated export directly.
