# QCP public model and vocabularies

This repository maintains the shared QCP model and ACQF/ETF validation contracts
for https://data.acqf-qcp.africa/. Work on `dev2` produces previews; it does not
change the live website or application deployments.

## Source and output

- `common/`: shared model, vocabularies, contexts, authored documentation and templates.
- `products/acqf/`, `products/etf/`: product profiles, vocabulary snapshots, authored guidance, assets and tests.
- `publication/`: shared queries, lean shell entry points and test fixtures; orchestration lives in `build.gradle.kts`.
- `build/site/`: clean generated website and the canonical release/preview artifact.
- `docs/`: checked-in compatibility export for existing Pages/raw GitHub consumers; do not edit.

ACQF keeps its existing URLs. ETF uses `/etf/`. Folder names in the source tree do
not rename RDF identifiers or published resources.

## Build and verify

With Java 21+, Bash, jq and GNU coreutils installed:

```bash
./gradlew buildSite              # clean website, models and model-reference pages
./gradlew buildValidation        # build site and refresh committed docs/ export
./gradlew check                  # rebuild site; check exports and both contracts
./gradlew checkDocumentation     # quick catalogue-to-page link check
./gradlew generateNqfList        # optional: refresh NQF titles in authored ACQF page
```

Jena is installed with checksum verification under `build/tools/`. No Python,
SHACL Play, visualization JAR or external ontology download is needed. References
are generated directly from each product's assembled validation graph. The retained
ACQF SVG is a legacy static illustration; see [its provenance](products/acqf/README.md).

Commit generated compatibility exports with their source changes. CI uploads only
the clean `build/site/` tree. Keep local experiments in ignored `sandbox/` or outside
the checkout; files left in `docs/` are not release inputs.

See [publication architecture](publication/README.md) and
[deployment coordination](publication/DEPLOYMENT.md) before rolling out the site
or changing either application's validation URL.
