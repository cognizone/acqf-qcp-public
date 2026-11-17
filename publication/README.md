# Shared publisher, product-owned sources

One repository builds ACQF and ETF for `https://data.acqf-qcp.africa/`.
ACQF keeps every existing published path and RDF identifier. ETF is published under
`/etf/`. Source organization is independent of public URLs.

```text
common/
  model/exchange-model.ttl       shared rules and shared CV bindings
  vocabularies/                 published shared CVs
  contexts/                     published import contexts
  frames/                       published import frame
  validation-inputs/            private frozen ISCED-F input
  publication.json              shared website mappings
  documentation/                shared reference page
products/
  acqf/
    profile.ttl                 ACQF-specific bindings and restrictions
    vocabularies/               catalogue and national/regional schemes
    model/                      existing ACQF model extension
    documentation/              existing landing page and model reference
    examples/
    tests/                      ACQF contract expectations
    publication.json            profile, site mappings and export path
  etf/
    profile.ttl
    vocabularies/               ETF catalogue and complete EQF distribution
    documentation/
    tests/
    publication.json
publication/                    shared Bash/Jena builder, queries and test runner
docs/                           generated, deployable website; do not edit
```

`common/publication.json` maps shared sources to their existing published locations.
Product configurations declare their profile, test expectations, site mappings and
export path. Source paths are relative to the configuration's directory; `output`
is relative to the repository and mapping `target` values are relative to the website.
Neither builder contains ACQF/ETF conditionals.

Validation reads the RDF-only directories `common/vocabularies/`,
`common/validation-inputs/` and each product's `vocabularies/`. There are no separate
file inventories. Profiles and catalogue links select which schemes constrain a
property; an unreferenced legacy scheme does not expand the accepted values.
Missing, empty and duplicate scheme definitions still fail validation assembly.
Keep prose and non-RDF files outside these directories.

## Build and check

Install Java 21+, Bash, jq and GNU coreutils:

```bash
./gradlew buildValidation       # assemble the complete website and both models
./gradlew check                 # check site copies and both validation contracts
```

Gradle Kotlin handles filesystem operations, JSON configuration, named-graph staging
and the build/check lifecycle. Jena's CLI runs the RDF queries and validation.
The Bash entry points are short compatibility wrappers:

```bash
bash publication/site.sh build
bash publication/site.sh check
# While editing only one validation profile:
bash publication/publish.sh check etf
```

The `setupJena` task installs the official CLI and verifies its archive against
`publication/jena.sha512`. No separate application or dependency resolver is compiled.
Publication uses reviewed local inputs; it does not fetch vocabularies or imports.
The source files are staged in separate named graphs. SPARQL resolves scheme and
catalogue bindings and rejects missing/empty/ambiguous inputs. Gradle groups the
ordered result into explicit `sh:in` lists. Structural constraints and prefix validators remain intact.

The complete website is assembled in ignored `build/` first. Only after both models
have been assembled successfully are declared outputs copied into `docs/`. Static
resource mappings reject duplicate destinations. Unrelated local files are never
deleted. When removing or relocating a published resource intentionally, remove its
old tracked output too; check rejects obsolete tracked files. Stage new outputs and
source moves before checking, so the tracked-file check sees the intended layout.

Check compares each generated resource with its maintained source, reconstructs
both validation graphs, compares RDF graphs including blank nodes, and runs the
shared level, cardinality, diagnostic and duplicate-notation fixtures. The complete
site check runs product-independent malformed-input tests once; the direct
`publish.sh check <product>` wrapper runs `checkModels -Pproduct=<product>` for
that product's graph and contract checks.
The product-owned `products/acqf/tests/published-paths.txt` also protects all 46
established ACQF public files. All 161 ACQF levels and eight EQF levels are exercised. ACQF additionally compares
against its historical complete validation graph. Changing source file locations
must not change that graph.

## Published paths

| Content | URL path |
| --- | --- |
| ACQF landing page | `/` (existing anchors and navigation preserved) |
| ACQF model documentation | `/exchange-model/exchange-model.html` |
| ACQF complete validation graph | `/model/exchange-model.ttl` |
| Existing ACQF vocabularies, contexts, examples | Existing `/model/...` URLs |
| ETF landing page | `/etf/` |
| ETF complete validation graph | `/etf/model/exchange-model.ttl` |
| ETF catalogue, EQF and profile | `/etf/model/...` |
| Shared reference page | `/common/` |
| Shared source model | Existing `/model/common/exchange-model.ttl` |

ACQF does not acquire an `/acqf/` URL prefix. There are no ACQF compatibility aliases
or redirects. Maintained sources and generated delivery copies have separate roles;
only the sources are edited. RDF identifiers remain unchanged, including the
original ETF catalogue namespace and official EU EQF scheme/member IRIs.

The existing ACQF HTML/SVG reference is retained. Its SHACL Play refresh command
writes to `products/acqf/documentation/`, then the site build publishes it at the
original URL. Run `./gradlew generateNqfList` to refresh vocabulary titles in the source landing
page, then rebuild the site. It uses the existing Jena CLI to read RDF; country
ordering, links and placeholders are maintained once in the marked HTML list.
The title refresh preserves those editorial entries. Do not edit these resources
in `docs/`.

## Publication boundary

The `dev2` workflow checks both products and uploads the whole validated `docs/`
tree as one preview artifact. It neither deploys the live website nor changes any
application setting. See [DEPLOYMENT.md](DEPLOYMENT.md) for the separate rollout.
Acc and production are outside this work.

Shared contexts and CVs remain single maintained sources. The old South Africa
`za-qf-levels` document is retained at its original URL for compatibility; the current
catalogue uses `za-nqf-levels`. This layout does not rename either scheme.

## Snapshot provenance

The complete EQF Turtle is the lossless serialization of the official 20260923-0
RDF/XML release at:

https://op.europa.eu/o/opportal-service/euvoc-download-handler?cellarURI=http%3A%2F%2Fpublications.europa.eu%2Fresource%2Fdistribution%2Feuropean-qualification-framework%2F20260923-0%2Frdf%2Fskos_ap_eu%2Feuropean-qualification-framework-skos-ap-eu.rdf&fileName=european-qualification-framework-skos-ap-eu.rdf

Original source SHA-256:
`df1a574c4ab1c1d3559c631057d0aac67abcc1342d6a7b329423676384cea25a`.
Its scheme is `http://data.europa.eu/snb/eqf/25831c2`.

`common/validation-inputs/isced-f.jsonld` is QCP's frozen lookup from commit
`c79c70c98078f5857793ec19cc6dbff94abb0f9c`, originally at
`backend/src/main/resources/concept-schemes/http___data.europa.eu_snb_isced-f_25831c2`.
It is a build input, not a second published ISCED vocabulary. Vocabulary refresh
is deferred; update QCP's packaged lookups when accepted membership changes.
