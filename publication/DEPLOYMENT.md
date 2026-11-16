# Coordinating the shared site and application deployments

Work is isolated on `dev2`. No live website settings, existing deployment branches,
application configuration or running services are changed by the preview workflow.

## One website release

Both product graphs, catalogues and supporting files are maintained in ACQF public.
The intended public website is `https://data.acqf-qcp.africa/`.

| Consumer | Validation contract on the shared website |
| --- | --- |
| ACQF-dev | `https://data.acqf-qcp.africa/model/exchange-model.ttl` |
| ETF-dev | `https://data.acqf-qcp.africa/model/etf/exchange-model.ttl` |

The ACQF endpoint and graph stay compatible. ETF is an additional path with EQF
constraints. Both belong to one validated `docs/` tree from one commit. Publishing
that complete tree avoids a partially updated combination of ACQF and ETF files.
The current workflow uploads a preview artifact and has no deployment job. Existing
Pages publishing-source settings remain unchanged and must be confirmed at rollout.

## Current application dependencies

The QCP application currently selects validation with
`env.acqf.qcp.exchange-format.shapes`. Its product-specific development profiles
select raw GitHub documents from separate public repositories:

- ACQF-dev: `backend/src/acqf/resources/application-acqf-dev.yml` selects the ACQF
  public repo's `dev/docs/model/exchange-model.ttl`.
- ETF-dev: `backend/src/etf/resources/application-etf-dev.yml` selects the ETF public
  repo's `dev/docs/model/exchange-model.ttl`.
- Local product profiles also contain development URLs and must be considered when
  making the eventual configuration change.

These application files have not been edited in this work. Their current working
tree already contains a separate product-resource refactoring. Do not merge site
migration changes into that work implicitly. The application loads shapes at
startup, so publishing a file alone does not switch a running application's graph.

For an explicitly configured canary, after pushing/reviewing `dev2`, the two preview
URLs from the actual ACQF repository are:

```text
https://raw.githubusercontent.com/cognizone/acqf-qpc-public/refs/heads/dev2/docs/model/exchange-model.ttl
https://raw.githubusercontent.com/cognizone/acqf-qpc-public/refs/heads/dev2/docs/model/etf/exchange-model.ttl
```

The repository's Git remote uses `acqf-qpc-public`; the domain and model namespace
continue to use `acqf-qcp`. Use the actual repository spelling for raw GitHub URLs.
These preview URLs do not publish the public website and are not automatic
replacements in existing deployments.

## Rollout order after approval

1. Run `./gradlew buildValidation check` on one reviewed source revision. CI must
   pass both contracts. Review the complete `public-data-site-<SHA>` preview, including
   both exported graphs and the unchanged shared resources.
2. Exercise both product configurations in dedicated canaries using those reviewed
   artifacts. Confirm ACQF national levels and all eight official EQF levels, as
   well as rejection of the other product's national-level values where applicable.
3. Publish the **entire** validated `docs/` tree to the existing website through its
   configured publication process. Preserve the domain and ACQF paths. Verify both
   public validation files are reachable before switching either application.
4. In the application deployment configuration, select the corresponding shared-site
   URL for each product. Package matching product lookup resources and deploy/restart
   ACQF-dev and ETF-dev. Membership or identifier changes require coordinated lookup
   updates; this consolidation itself preserves existing identifiers and membership.
5. Check startup validation loading, product framework choices and sample imports in
   both deployments. Rebuild concept indexes only if their membership/content changed.
6. Retire ETF's old publication pipeline only after no deployment depends on its old
   raw `dev` URL. Leave that branch available during migration. Do not promote the
   ETF `dev2` retirement deletions into a still-consumed deployment branch early.

ACQF and ETF application rollouts need not occur simultaneously: the site addition
is backward compatible for ACQF. They must consume the intended product contract
and compatible lookup data from the reviewed release.

## Rollback

Keep the previous complete website artifact/commit and previous application URL and
lookup-resource settings. Roll back the website as one tree, not individual product
files. An application can revert to its prior URL/settings while the old publication
branch remains available. Acceptance/production deployment profiles and resources
are outside the `dev2` cutover until separately reviewed.
