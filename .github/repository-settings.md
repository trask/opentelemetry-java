# Repository settings

Repository settings in addition to what's documented already at
<https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md>.

## General > Pull Requests

* Allow squash merging > Default to pull request title

## Actions > General

* Fork pull request workflows from outside collaborators:
  "Require approval for first-time contributors who are new to GitHub"

  (To reduce friction for new contributors,
  as the default is "Require approval for first-time contributors")

## Branch protections

The order of branch protection rules
[can be important](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/defining-the-mergeability-of-pull-requests/managing-a-branch-protection-rule#about-branch-protection-rules).
The branch protection rules below should be added before the `**/**` branch protection rule
(this may require deleting the `**/**` rule and recreating it at the end).

### `main`

* Require branches to be up to date before merging: UNCHECKED

  (PR jobs take too long, and leaving this unchecked has not been a significant problem)

* Status checks that are required:

  * EasyCLA
  * required-status-check

### `release/*`

Same settings as above for `main`, except:

* Restrict pushes that create matching branches: UNCHECKED

  (So that opentelemetrybot can create release branches)

### `renovate/**/**`, and `opentelemetrybot/*`

* Require status checks to pass before merging: UNCHECKED

  (So that renovate PRs can be rebased)

* Restrict who can push to matching branches: UNCHECKED

  (So that bots can create PR branches in this repository)

* Allow force pushes > Everyone

  (So that renovate PRs can be rebased)

* Allow deletions: CHECKED

  (So that bot PR branches can be deleted)

### `benchmarks`

- Everything UNCHECKED

  (This branch is currently only used for directly pushing benchmarking results from the
  [overhead benchmark](https://github.com/open-telemetry/opentelemetry-java/actions/workflows/benchmark.yml)
  job)

## Secrets and variables > Actions

* `GPG_PASSWORD` - stored in OpenTelemetry-Java 1Password
* `GPG_PRIVATE_KEY` - stored in OpenTelemetry-Java 1Password
* `SONATYPE_KEY` - owned by [@jack-berg](https://github.com/jack-berg)
* `SONATYPE_USER` - owned by [@jack-berg](https://github.com/jack-berg)
