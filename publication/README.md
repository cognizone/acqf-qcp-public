# Exchange-model publication (dev)

Maintain `docs/model/common/exchange-model.ttl` for structural rules and shared CV
bindings. Maintain `docs/model/acqf-profile.ttl` for ACQF-specific NQF bindings and
namespace policy. Keep existing shape and vocabulary IRIs. Profiles add constraints;
they do not override common rules.

`docs/model/exchange-model.ttl` is the generated, complete ACQF validation graph.
Its URL retains its existing meaning. The common model alone is not a complete product
validation contract. Acc and public have not been changed by this dev work.

With Java 21 installed:

```
./gradlew buildValidation
./gradlew check
```

Gradle resolves Apache Jena 5.6.0 and JUnit from Maven Central; no Python, Jena binary
installation or other RDF tool is required. Generation uses Jena's Turtle writer.
Checks compare RDF graphs, including blank-node structure, rather than serializer
formatting. Commit the generated file with its source changes.

The GitHub Action runs `./gradlew check` on PRs targeting dev and pushes to dev. It
rebuilds the model in memory, checks the committed export, and runs JUnit tests. It
never commits files or deploys a site. Site publication settings are unchanged.

The builder reads only reviewed local inputs. `controlledVocabulary` binds a property
to a scheme's explicit `skos:inScheme` members. `controlledVocabularyCatalogue` binds
it to all schemes referenced by `dct:relation` from the catalogue's members. Each
framework must reference exactly one scheme. Missing, empty or invalid inputs fail
the build. The output replaces both annotations with one `sh:in` list per shape;
existing prefix constraints and their SPARQL validator remain. No imports are fetched.
Use the export with a SHACL engine supporting SHACL-SPARQL; no QCP preprocessing is needed.

Vocabularies under this publisher's namespace resolve to existing `docs/model/` files.
Other reviewed inputs are mapped in `publication/common-vocabularies.json` or the
product's `publication/validation.json`. `publication/isced-f.jsonld` freezes the
unchanged ISCED-F lookup from QCP commit `c79c70c98078f5857793ec19cc6dbff94abb0f9c`, path
`backend/src/main/resources/concept-schemes/http___data.europa.eu_snb_isced-f_25831c2`.
It does not define a new vocabulary. Prefix-only bindings stay prefix-only.

The regression compares the complete ACQF graph with dev commit
`1e07dbd139a815427e6aeec8ba431adda5c25681` after the original vocabulary expansion.
It preserves Tunisia, all 161 NQF values, shared bindings, cardinalities, severities,
prefix diagnostics and graph-scoped duplicate-notation validation. A deliberate ACQF
contract change requires reviewing and updating that baseline.

ETF pins this repo as a Git submodule and applies `publication/publisher.gradle.kts`.
The same Java sources assemble and test both products. Update ETF's gitlink explicitly
after reviewing common changes. Package matching QCP lookups when membership changes.
