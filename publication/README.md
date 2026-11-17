# Publication architecture

One repository builds ACQF and ETF for `https://data.acqf-qcp.africa/`. Maintained
source and generated delivery artifacts have separate owners and locations:

```text
common/
  model/                        shared structural shapes and CV bindings
  vocabularies/                 published shared CVs
  validation-inputs/            private frozen ISCED-F input
  contexts/, frames/            published import resources
  documentation/                authored shared guidance
  templates/                    source templates for generated model references
  publication.json              shared source-to-URL mappings
products/<product>/
  profile.ttl                   product-specific CV bindings and restrictions
  vocabularies/                 product catalogue and reviewed vocabulary snapshots
  documentation/                authored product guidance
  assets/                       optional static assets, with product-owned provenance
  examples/, model/             optional product examples and legacy extensions
  tests/                        product expectations and compatibility contracts
  publication.json              profile, source-to-URL mappings and reference output
publication/                    shared queries, fixtures and thin shell wrappers
build/site/                     clean, disposable website; the release artifact
docs/                           committed compatibility export, generated only
```

Sources resolve relative to their configuration file. Mapping targets are public
paths relative to the website root; `output` and `reference.output` retain the
`docs/` prefix to describe the committed compatibility location. They map to the
same relative paths in `build/site/`.

## One-way generation

```text
RDF sources + product profile -> assembled SHACL -> generated HTML reference
Authored pages + static assets ---------------------------> clean build/site/
clean build/site/ -> validated release artifact
clean build/site/ -> committed docs/ compatibility export
```

No generation task reads from `docs/`. HTML reference pages belong only in generated
outputs. Authored prose and templates stay in the source tree. The retained ACQF
SVG is an explicitly documented static legacy asset, not a representation that the
build claims to regenerate from current RDF.

The model reference renderer uses the existing Jena CLI and a small HTML template.
It shows class/property constraints and vocabulary memberships from the assembled
product graph. Structured constraints link to the complete SHACL model. Existing
ACQF reference URL fragments are preserved by a product-owned compatibility test.
It needs no external ontology, visualization engine or additional runtime/tool JAR.

## Commands

```bash
./gradlew buildSite                 # recreate clean build/site/ from source
./gradlew buildValidation           # build site; update committed docs/ outputs
./gradlew check                     # rebuild site; verify all contracts and exports
./gradlew checkDocumentation        # quick catalogue-to-page link check
bash publication/publish.sh check etf  # check only one product's validation graph
```

Gradle Kotlin handles filesystem/configuration work. Bash wrappers are entry points;
Jena performs RDF parsing, querying, graph comparison and SHACL validation. The only
downloaded RDF tool is the official Jena distribution, pinned by
`publication/jena.sha512` and installed under ignored `build/tools/`.

Validation loads the RDF-only directories `common/vocabularies/`,
`common/validation-inputs/` and each product's `vocabularies/`. No file inventory is
maintained separately. The profile and catalogue select accepted schemes. Unused
legacy schemes do not expand accepted values. Missing, empty, ambiguous and duplicate
scheme inputs fail. Non-RDF prose belongs outside these directories.

Generation stages every model and reference before replacing `build/site/`. It
recreates that output from scratch, so removed resources and local scratch files
cannot leak into a release. `docs/` is refreshed only by `buildValidation`, which
removes obsolete tracked generated outputs and preserves unrelated untracked local
files. Such local files are never copied into the canonical artifact. Do not deploy
a dirty compatibility directory directly.

## Checks

The full check rebuilds the clean site and compares it with committed `docs/`.
Static pages and generated references are compared as bytes; validation models are
compared as RDF graphs, including blank nodes. Obsolete tracked outputs fail.

Shared malformed-input fixtures run once. Product tests exercise all 161 ACQF levels
and eight EQF levels, cardinalities, invalid/mixed values, diagnostics and duplicate
notation. ACQF additionally pins its historical complete validation graph, all 46
established public files and existing model-reference anchors.

Catalogue-to-documentation checks resolve each referenced scheme to its local RDF
source and require a link from the product landing page to the published vocabulary.
This checks actual RDF identities, including EQF's original EU scheme IRI. Country
ordering and under-development placeholders remain editorial HTML. Run
`./gradlew generateNqfList` to refresh linked titles from RDF, then rebuild exports.

## Public paths and deployment

| Content | Public path |
| --- | --- |
| ACQF landing page | `/` |
| ACQF generated reference | `/exchange-model/exchange-model.html` |
| ACQF retained diagram | `/exchange-model/exchange-model.svg` |
| ACQF validation graph | `/model/exchange-model.ttl` |
| Existing ACQF vocabularies, contexts and examples | Existing `/model/...` paths |
| ETF guidance | `/etf/` |
| ETF generated reference | `/etf/model/reference.html` |
| ETF validation graph and vocabularies | `/etf/model/...` |
| Shared guidance | `/common/` |

ACQF has no new URL prefix or compatibility aliases. RDF identifiers remain intact.
The older South Africa `za-qf-levels` resource stays available at its existing URL;
the current catalogue binds `za-nqf-levels`.

CI runs the complete check and uploads only `build/site/` as one preview artifact.
It never commits documents or deploys the live site. Existing raw GitHub consumers
still require committed `docs/`, so retain that checked export until an explicit
consumer/hosting migration. See [DEPLOYMENT.md](DEPLOYMENT.md). Acc and production
are outside this work.

## Snapshot provenance

The complete EQF Turtle is the lossless serialization of the official 20260923-0
RDF/XML release:

https://op.europa.eu/o/opportal-service/euvoc-download-handler?cellarURI=http%3A%2F%2Fpublications.europa.eu%2Fresource%2Fdistribution%2Feuropean-qualification-framework%2F20260923-0%2Frdf%2Fskos_ap_eu%2Feuropean-qualification-framework-skos-ap-eu.rdf&fileName=european-qualification-framework-skos-ap-eu.rdf

Original SHA-256: `df1a574c4ab1c1d3559c631057d0aac67abcc1342d6a7b329423676384cea25a`.
Its scheme remains `http://data.europa.eu/snb/eqf/25831c2`.

`common/validation-inputs/isced-f.jsonld` is QCP's frozen lookup from commit
`c79c70c98078f5857793ec19cc6dbff94abb0f9c`, originally at
`backend/src/main/resources/concept-schemes/http___data.europa.eu_snb_isced-f_25831c2`.
It is a private build input, not another published ISCED vocabulary. Refresh is
deferred; coordinate membership changes with QCP's packaged lookup resources.
