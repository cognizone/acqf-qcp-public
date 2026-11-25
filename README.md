# QCP model and vocabulary sources

This repository maintains the shared QCP model and ACQF/ETF product definitions.
Push source changes to `dev2`: GitHub Actions builds, validates and uploads the
complete **DEV preview artifact**. Local builds are optional.

**The live ACQF website is used by PROD and is frozen.** This branch has no deployment
job and does not change hosting settings, application URLs, acc or production.

## Source and output

- `common/`: shared model, vocabularies, contexts, authored guidance and templates.
- `products/acqf/`, `products/etf/`: product profiles, vocabulary snapshots, guidance, assets and tests.
- `src/main/kotlin/publication/`: the publication tool; model assembly, references and catalogue helpers.
- `src/test/kotlin/publication/`: publication boundary and failure-handling tests.
- `publication/`: shared queries, RDF test fixtures and lean shell entry points.
- `build/site/`: complete generated artifact, built by CI; never committed.
- `docs/`: frozen legacy snapshot retained for existing consumers and hosting compatibility. It is not a build input, output or freshness requirement.

Publication manifests declare a `publicBaseUrl` for common, ACQF and ETF. Product
`basePath` controls the combined artifact layout; legacy URL exceptions remain
explicit. Source folders and standard output names follow conventions.
Cross-publication documentation links use the configured public URLs, allowing
separate domains later without rewriting RDF identifiers or changing deployment.
The common source model and product profiles stay in the repository as build inputs;
only assembled validation models are published. Consumer-facing paths and RDF IRIs
are preserved in the generated artifact. ETF uses `/etf/`.
The model and profiles evolve with application constraints; domain vocabularies
are pluggable content under stable bindings. Workflow status semantics remain
coupled to application behavior. No current website or application consumer is
switched to this artifact automatically.

## Normal workflow

1. Edit maintained sources and product expectations.
2. Commit and push to `dev2`, or open a pull request targeting it.
3. CI runs the build and checks, enforces that it changed no repository files, and
   uploads `public-data-site-<commit SHA>` on success.
4. Download the artifact for review. A future deployment must target a separately
   selected DEV environment; deployment is outside this workflow.

Do not regenerate or commit `docs/` for source changes. Its raw GitHub URLs continue
to expose the retained snapshot, not the latest generated preview.

## Optional local checks

With Java 21 installed, Bash, jq and GNU coreutils:

```bash
./gradlew check                  # build and validate build/site/ from source
./gradlew buildSite              # build an optional local preview
./gradlew checkDocumentation     # quick catalogue-to-page link check
./gradlew generateNqfList        # optional authoring helper for NQF titles
```

`buildValidation` remains an alias for `buildSite`. All build entry points write
only under ignored `build/`; none refresh `docs/`. Model-only builds use
`build/models/`. Gradle compiles the Kotlin publication tool and runs its tests as
part of `check`. Jena is checksum-pinned under `build/tools/`; no Python, SHACL Play,
visualization JAR or external ontology download is needed.

See [publication architecture](publication/README.md),
[the DEV deployment boundary](publication/DEPLOYMENT.md), and
[the retained diagram's provenance](products/acqf/README.md).
