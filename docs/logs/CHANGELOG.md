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
- Introduced `Contact#update(...)` so multi-field updates validate every value
  before committing changes, preventing partially updated contacts.
- Published the Task entity/service implementation plan in
  `docs/architecture/2025-11-19-task-entity-and-service.md` and added the ADR
  catalog (`docs/adrs/README.md`) plus ADR-0001 through ADR-0008 to capture
  validation, storage, atomic update, CI, SpotBugs runtime, documentation, Task
  feature choices, and the CI metrics summary script.
- Added `scripts/serve_quality_dashboard.py` (packaged with the `quality-reports-*`
  artifact) so contributors can spin up a local HTTP server to view the React QA dashboard.
- Added personal design notes under `docs/design-notes/notes/` (CI gates, metrics script,
  Contact entity/service behaviors, SpotBugs plan, docs structure, and test strategy) to
  capture the reasoning behind each piece of the implementation.
- Added Shields.io badges for JaCoCo line coverage, PITest mutation score, SpotBugs status,
  and Dependency-Check findings at the top of the README, plus a license badge for quick
  at-a-glance metadata.
- Updated README badges to the bolder “for-the-badge” style with GitHub/Codecov logos so the status bar matches the secure snapshot look-and-feel; static-analysis badges now use the same passing green.
- Checked off every item in `docs/requirements/task-requirements/requirements_checklist.md` now that the Task entity/service and tests are implemented.
- Finalized `docs/architecture/2025-11-19-task-entity-and-service.md` (status Implemented, summary, DoD results, deviations) so the Task design record reflects the delivered code.
- Published ADR-0009 describing the permanent unit-test strategy (layered test classes,
  AssertJ + parameterized tests, singleton reset helpers, and CI enforcement via JaCoCo/PITest).

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
- Extended `ValidationTest` with explicit null-path checks so the helper’s
  `validateNotBlank` logic triggers before length/numeric math.
- Removed redundant `validateNotBlank` calls from `Contact` so setters and the
  constructor rely solely on the shared `Validation` helpers (eliminating the
  equivalent PIT mutants and enabling a 100% mutation score).
- Contact setters now trim first/last names and addresses after validation to
  avoid persisting accidental leading/trailing whitespace.
- Contact IDs are now trimmed on construction, and `Validation.validateLength`
  measures trimmed length so validation matches the data actually stored.
- Expanded Checkstyle configuration (imports, indentation, line length, braces, etc.)
  and wired `spotbugs-maven-plugin` (auto-skipped on JDK 23+) so bug patterns fail
  `mvn verify`.
- `ContactService#getDatabase()` now returns an unmodifiable `Map.copyOf(...)`
  snapshot and a new `clearAllContacts()` helper was added for tests, eliminating
  SpotBugs exposure warnings for the service internals.
- `ContactService#updateContact` now routes through the atomic
  `Contact#update(...)` helper so updates either fully apply or leave the contact
  unchanged while reusing the constructor’s validation messages.
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
- Clarified README sections describing the `ConcurrentHashMap<String, Contact>`
  storage, the `Map.copyOf(...)` snapshot, and the atomic update helper managed
  by `ContactService`.
- Corrected README documentation to state that SpotBugs currently runs on all
  supported JDKs (17/21/25) instead of implying it auto-skips on newer runtimes.
- Enhanced `scripts/ci_metrics_summary.py` to show colored icons/bars, severity
  breakdowns, and generate a dark-mode `target/site/qa-dashboard/index.html`
  page with quick links to JaCoCo/SpotBugs/Dependency-Check/PITest reports.
- React-based QA dashboard (`ui/qa-dashboard`) now builds during CI, consumes the
  generated `metrics.json`, and replaces the old static HTML so artifacts ship a
  fully interactive console.
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
- Converted the Validation helper docs to describe all domain fields (Contact + Task) now that both models share the same utility.
- Added README links in each subheading so readers can jump directly to the
  relevant file (`Contact.java`, `ContactTest.java`, `ContactService.java`,
  `ContactServiceTest.java`) from the design/testing sections.
- Added explicit placeholders/doc comments in `ContactService.java` and
  `ContactServiceTest.java` so the service layer mirrors the structure of the
  `Contact`/`ContactTest` pair while we flesh out CRUD behavior.
- Completed the README sections for `Task.java`/`TaskTest.java` and `TaskService.java`/`TaskServiceTest.java`, describing validation flow, error philosophy, and scenario coverage instead of TODO placeholders.
- README now matches the implementation details: `ContactService.updateContact` references `Contact.update(...)`, `validateNumeric10` lists the `requiredLength` parameter, and SpotBugs/JDK matrix text reflects the actual `{17, 21}` CI coverage.
- README badges now use a uniform Shields.io flat-square style (GitHub Actions, Codecov, JaCoCo, PITest, SpotBugs, OWASP DC, License) with consistent colors (brightgreen for CI/coverage/mutation, blue for static analysis/license).
- Added `TaskServiceTest.testClearAllTasksRemovesEntries` (with a note explaining it) so PIT kills the last surviving mutant that removed the internal `Map.clear()` call.
- `ContactService#updateContact` and `TaskService#updateTask` now validate/trim
  the incoming ids (matching the delete paths) so lookups succeed even if
  callers include whitespace, and both services now throw `IllegalArgumentException`
  for blank ids on update; new unit tests cover the trimmed-id success path and
  blank-id error case.
- Refreshed `README.md` and `index.md` to correct Task file paths (now under
  `contactapp`), link to the new architecture/ADR directories, and fix stale
  relative links left over from the original docs layout.
- Updated `ui/qa-dashboard` to emit relative asset paths, fixed report links,
  and documented the new `serve_quality_dashboard.py` helper so the downloaded
  dashboard renders correctly when opened locally or via the bundled server.
- Extended `scripts/ci_metrics_summary.py` so Dependency-Check uses the shared
  `target/` constant, PITest "detected" counts flow into summaries, and the QA
  report now includes a dependency severity breakdown row plus ADR tracking.
- Enhanced `scripts/ci_metrics_summary.py` to optionally write badge JSON for JaCoCo,
  PITest, SpotBugs, and Dependency-Check when `UPDATE_BADGES=true`, so badges stay in sync.

### Security
- Bumped `org.pitest:pitest-maven` to `1.21.1` and `org.pitest:pitest-junit5-plugin`
  to `1.2.3` for the latest mutation-testing fixes.
- Upgraded `org.owasp:dependency-check-maven` to `12.1.9` for the most recent
  CVE feed handling improvements.
