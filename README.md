# QCP public model and vocabularies

This repository maintains the shared QCP model and product-specific ACQF/ETF
validation contracts for https://data.acqf-qcp.africa/. On `dev2`, both products are
checked together and packaged as one preview site. Existing deployments are unchanged.

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
common model or a product profile, then regenerate both complete exports; do not
edit generated validation files directly. See [deployment coordination](publication/DEPLOYMENT.md)
before moving either application or the public website to the consolidated outputs.
