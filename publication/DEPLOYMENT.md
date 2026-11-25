# DEV preview boundary — no production deployment

The existing `https://data.acqf-qcp.africa/` website is used by PROD. It is frozen.
Do not publish this artifact there, change its Pages source/domain/settings, update
acc/public branches, or switch current application configuration as part of this work.

## What GitHub Actions does

On pushes and pull requests targeting `dev2`, the workflow:

1. Builds a clean `build/site/` entirely from maintained sources.
2. Validates ACQF and ETF contracts, model references, catalogue links and public paths.
3. Checks that the build left the Git working tree unchanged.
4. Uploads `build/site/` as `public-data-site-<commit SHA>`.

There is no deployment job, Pages write permission, environment deployment,
repository dispatch, application rollout or bot commit. Source changes never require
a developer to regenerate and commit website files locally.

## Retained compatibility snapshot

The tracked `docs/` tree is deliberately frozen. No task reads it as generation
input, writes it, or requires it to match new source changes. Existing raw GitHub
consumers and existing hosting arrangements are left alone. A raw URL under
`dev2/docs/` is a legacy snapshot, not a preview of the latest source.

The ACQF regression baseline is also frozen, but has a different role: its explicit
historical commit/path in `products/acqf/tests/contract.json` is read from Git objects
to preserve the approved validation contract. It does not depend on working-tree
`docs/` or make that folder a required build artifact.

## Future DEV rollout

When a DEV target has been explicitly selected and authorized:

1. Configure `publicBaseUrl` in the common, ACQF and ETF manifests for the selected
   DEV destinations, then choose a successful revision and its matching artifact.
   These settings control cross-publication links, not deployment or RDF IRIs.
2. Deploy the whole artifact to that separate DEV target, preserving relative paths.
3. Verify `/model/exchange-model.ttl`, `/etf/model/exchange-model.ttl` and the product
   documentation on the DEV target.
4. Change only explicitly selected development/local application configurations to
   those DEV URLs and compatible vocabulary lookups; test startup and sample imports.

No DEV hostname or deployment mechanism is assumed or configured here. RDF identifiers
can keep their established namespaces while documents are served from a DEV URL.
Existing production URLs, the live ACQF website and production application consumers
stay unchanged. This procedure does not authorize a later production rollout.

## DEV rollback

Keep the preceding DEV artifact and development application settings. Roll back the
DEV site as one artifact, and revert only the development settings changed in that
rollout. Production hosting and production settings are outside this process.
