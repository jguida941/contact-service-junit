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
- Java CI workflow now installs Python 3.12 for every matrix leg so the QA
  summary script runs reliably on Windows and Ubuntu runners.
- CodeQL workflow now pins Temurin JDK 17, caches Maven deps, enforces
  concurrency/path filters, and runs the `+security-and-quality` query pack via
  CodeQL autobuild for broader coverage.
- Java CI’s Codecov upload now reads the token from the job env to remain
  parseable for forked PRs where the `secrets` context is unavailable.
- Java CI explicitly passes `-DnvdApiKey`/delay flags to Maven verify so the
  built-in Dependency-Check execution always receives the configured secret.
- Maven Compiler now uses `<release>17</release>` via `maven.compiler.release`
  to align with the module system and eliminate repeated “system modules path”
  warnings during `javac`.
- Added `ValidationTest` to assert boundary acceptance and blank rejection in
  helper methods, giving PIT visibility into those behaviors.
- Expanded service tests to cover singleton reuse, missing delete branch, and a
  last-name change during updates so mutation testing can kill the remaining
  ContactService mutants.
- Fixed the Java CI workflow so `dependency-check.skip` uses the correct
  hyphenated property, Codecov upload keys off `secrets.CODECOV_TOKEN`, and
  quality-report artifacts no longer fail the job when reports are absent.
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
- CI flow diagram now includes the QA summary/Codecov step so the visual matches
  the workflow description.
- Restructured the README’s testing section so `ContactTest` and
  `ContactServiceTest` each have their own bullet list explaining scope and
  assertions.
- Created dedicated README sections for `ContactService` validation/testing with
  TODO placeholders so the service mirrors the structure already documented for
  `Contact`.
- Clarified README headings/TOC so each section explicitly references the source
  file (`Contact.java`, `ContactTest.java`, `ContactService.java`,
  `ContactServiceTest.java`).
- Validation section now explicitly links to `Validation.java` so readers know
  which helper backs the Contact rules.
- `ContactService` validation headings now link to the source file so both
  sections mirror each other.
- Added README links in each subheading so readers can jump directly to the
  relevant file (`Contact.java`, `ContactTest.java`, `ContactService.java`,
  `ContactServiceTest.java`) from the design/testing sections.
- Added explicit placeholders/doc comments in `ContactService.java` and
  `ContactServiceTest.java` so the service layer mirrors the structure of the
  `Contact`/`ContactTest` pair while we flesh out CRUD behavior.

### Security
- Bumped `org.pitest:pitest-maven` to `1.21.1` and `org.pitest:pitest-junit5-plugin`
  to `1.2.3` for the latest mutation-testing fixes.
- Upgraded `org.owasp:dependency-check-maven` to `12.1.9` for the most recent
  CVE feed handling improvements.
