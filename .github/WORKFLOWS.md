# GitHub Workflows

dev-console follows the same thin-wrapper workflow layout as Nano. Repository workflows should stay small and delegate Java build, release, publish, and maintenance behavior to shared workflows in `NanoNative/NanoNative`.

## Workflows

- `build-pr.yml` runs PR checks and manual checks through `wc_java_build_common.yml`.
- `build-merge.yml` publishes snapshots from `main` through `wc_java_release.yml`, then publishes the build artifact to Maven Central and GitHub Packages.
- `release.yml` is the manual release path. It builds through `wc_java_release.yml`, publishes Maven Central and GitHub Packages, then creates the GitHub release.
- `maintenance.yml` updates the Maven Wrapper through `wc_java_update_maven_wrapper.yml`.
- `dependabot.yml` opens weekly grouped GitHub Actions and Maven dependency PRs.

## Organization Maintenance

`NanoNative/NanoNative/.github/workflows/weekly-maintenance.yml` runs at the organization level. It scans active repositories, merges green non-draft PRs from `dependabot/*` and `bot/maintenance-*`, and runs extra validation for `bot/maintenance-*` PRs through `build-pr.yml`.

Dependabot still creates dependency PRs in this repository. The organization workflow does not create Dependabot PRs; it reviews and merges the ready ones.

## Secrets

Publishing uses the shared workflow secret names:

- `OSSH_USER` and `OSSH_PASS` mapped to Maven Central credentials
- `GPG_SIGNING_KEY` and `GPG_PASSPHRASE` for signed Central publications

## Pins

Shared workflow calls are pinned to commit SHAs, matching Nano's workflow style.
