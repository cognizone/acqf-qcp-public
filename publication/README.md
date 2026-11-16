# Exchange-model publication (dev)

Maintain `docs/model/common/exchange-model.ttl` for structural rules and shared CV
bindings. Maintain `docs/model/acqf-profile.ttl` for ACQF-specific NQF bindings and
namespace policy. Keep existing shape and vocabulary IRIs. Profiles add constraints;
they do not override common rules.

`docs/model/exchange-model.ttl` is a generated, complete ACQF validation graph.
Its URL retains its existing meaning. The common model alone is not a complete product
validation contract. Acc and public have not been changed by this dev work.

```
python3 -m pip install -r publication/requirements.txt
python3 tools/build-exchange-model.py
python3 tools/build-exchange-model.py --check
python3 tools/test-exchange-model.py
java -cp "$JENA_HOME/lib/*" tools/ValidateExchangeModel.java docs/model/exchange-model.ttl acqf
```

Use Apache Jena 5.6.0 and Java 17 or newer for the standalone validation checks.
Commit the generated file with its source changes. The GitHub Action runs the build
in check mode and the regression tests on PRs targeting dev and pushes to dev; it never
commits files or deploys a site. Existing site publication settings are unchanged.

The builder reads only reviewed local inputs. `controlledVocabulary` binds a property
to a scheme's explicit `skos:inScheme` members. `controlledVocabularyCatalogue` binds
it to all schemes referenced by `dct:relation` from the catalogue's members. Each
framework must reference exactly one scheme. Missing, empty or invalid inputs fail
the build. The output replaces both annotations with one `sh:in` list per shape;
existing prefix constraints and their SPARQL validator remain. No imports are fetched.
The result needs no QCP-specific preprocessing; use a SHACL engine with SHACL-SPARQL.

Vocabularies under this publisher's namespace resolve to the existing `docs/model/`
files. Other reviewed inputs are mapped explicitly in
`publication/common-vocabularies.json` or the product's `publication/validation.json`.
`publication/isced-f.jsonld` is the unchanged ISCED-F lookup from QCP commit
`c79c70c98078f5857793ec19cc6dbff94abb0f9c`, path
`backend/src/main/resources/concept-schemes/http___data.europa.eu_snb_isced-f_25831c2`.
It freezes the already accepted membership; it does not define a new vocabulary.
Country, language and ESCO prefix-only constraints remain prefix-only.

The regression compares the complete generated ACQF graph with dev commit
`1e07dbd139a815427e6aeec8ba431adda5c25681` after the original vocabulary expansion.
It preserves Tunisia, all 161 NQF values, all shared bindings, cardinalities, severities,
prefix diagnostics and the graph-scoped duplicate-notation rule. A deliberate future
ACQF contract change requires reviewing and updating this baseline.

ETF uses this same builder, common source and shared CV documents from a pinned ACQF
commit. To change shared rules, review ACQF dev first, then update ETF's pin and export.
Package matching vocabulary lookups in QCP whenever accepted membership changes.
