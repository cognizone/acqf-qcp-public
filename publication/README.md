# Publication architecture

One repository builds ACQF and ETF as a DEV preview artifact. The live ACQF
website used by PROD is frozen. Maintained sources and generated delivery artifacts
have separate owners and locations:

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
  publication.json              public context path and legacy URL exceptions
publication/                    shared queries, fixtures and thin shell wrappers
build/site/                     clean, disposable website; the release artifact
docs/                           frozen legacy snapshot; not part of the build
```

Product manifests describe publication intent only. They do not describe RDF
bindings, source file inventories, test locations or copy operations.

ACQF retains its existing root context and two legacy filename exceptions:

```json
{
  "basePath": "",
  "profilePath": "model/acqf-profile.ttl",
  "referencePath": "exchange-model/exchange-model.html"
}
```

ETF needs only its context:

```json
{ "basePath": "etf" }
```

`basePath` is relative to the generated website root. Optional `profilePath` and
`referencePath` override the final site-relative destination for compatibility;
they are not source locations. Optional `title` overrides the default
`<PRODUCT> model reference` page title. There is no separate manifest schema/DSL.

The shared build applies these folder and filename conventions once:

| Maintained source or generated output | Public path within the product context |
| --- | --- |
| `profile.ttl` | `model/profile.ttl` (unless overridden) |
| `vocabularies/` | `model/` |
| `documentation/`, optional `assets/` | Context root |
| Optional `examples/` | `model/examples/` |
| Optional `model/` extensions | `model/` |
| Assembled validation graph | `model/exchange-model.ttl` |
| Generated model reference | `model/reference.html` (unless overridden) |

Tests always live in the product's `tests/` folder. Shared publication destinations
are declared once in `common/publication.json` as a `paths` map from common resource
folders to existing site-relative locations. Private validation inputs and templates
are not in that map and are never copied wholesale into the site.

RDF models, profiles and catalogues remain the source of validation semantics.
RDF identifiers do not determine website context paths or legacy filenames.

## One-way generation

```text
RDF sources + product profile -> assembled SHACL -> generated HTML reference
Authored pages + static assets ---------------------------> clean build/site/
clean build/site/ -> validated release artifact
```

No build task reads from, writes to, or compares against working-tree `docs/`. HTML reference pages belong only in generated
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
./gradlew buildValidation           # compatibility alias for buildSite
./gradlew check                     # CI: build source and verify generated artifact
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
recreates that output from scratch and verifies it contains exactly the declared
publication targets. Local scratch files and removed resources cannot leak into a
release. All output stays under ignored `build/`; model-only builds use
`build/models/`. The tracked `docs/` snapshot is left unchanged.

CI performs the normal build and checks, then uploads the clean artifact. Developers
commit sources and expectations only; local builds are optional for previewing or
troubleshooting. No bot generates commits or synchronizes the legacy snapshot.

## Checks

The full check rebuilds the clean site and validates the artifact itself. It
verifies declared publication targets, both validation contracts, legacy URL paths
and reference anchors. It does not require any generated files in Git or compare
against the retained `docs/` tree. The immutable historical ACQF baseline is read
from its explicit Git commit/path; future intended contract changes require reviewing
that test expectation, not rewriting the production website snapshot.

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

CI runs the complete check, asserts the working tree stayed unchanged and uploads
only `build/site/` as a preview artifact. There are no deployment jobs or hosting
changes. In particular, `https://data.acqf-qcp.africa/` is a production dependency
and must not be updated. A future rollout can target only a separately selected
DEV environment. See [DEPLOYMENT.md](DEPLOYMENT.md).

Existing raw GitHub consumers continue to see the frozen `docs/` snapshot. It must
not be presented as the latest source preview. No current application URL is changed.

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
