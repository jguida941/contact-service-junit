# Changelog
All notable changes to this project will be documented here. Follow the
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) spirit and use
[Semantic Versioning](https://semver.org/) once formal releases begin.

## [Unreleased]
### Added
- Documented Sonatype OSS Index setup in `README.md` so dependency scans can be
  fully authenticated when desired.
- Introduced `scripts/ci_metrics_summary.py` and a GitHub Actions summary step
  so each matrix run publishes tests/coverage/mutation/dependency metrics.
- Added a "Sample QA Summary" section to `README.md` showing what the Actions
  summary table looks like for a representative run.
- Created `docs/backlog.md`, moving the detailed
  backlog/sample content out of the README to keep it focused.

### Changed
- Updated `README.md` and `docs/index.md` to match the new `docs/requirements`
  structure and clarify when the optional self-hosted mutation lane is used.
- Scheduled Dependabot’s weekly Maven check to run Mondays at 04:00 ET so
  updates happen predictably.
- README now documents why the `release-artifacts` job is skipped on PRs and
  tracks upcoming reporting enhancements.
- Added Codecov integration (GitHub Action upload + README instructions/badge).
- Clarified README sections describing the `HashMap<String, Contact>` storage
  managed by `ContactService`.
- Added a README note explaining why the service methods return `boolean`
  (simple success/failure signaling for this milestone).
- Updated README caching section to explain that Dependency-Check caches are
  intentionally purged each run (only Maven artifacts remain cached).

### Security
- Bumped `org.pitest:pitest-maven` to `1.21.1` and `org.pitest:pitest-junit5-plugin`
  to `1.2.3` for the latest mutation-testing fixes.
- Upgraded `org.owasp:dependency-check-maven` to `12.1.9` for the most recent
  CVE feed handling improvements.
