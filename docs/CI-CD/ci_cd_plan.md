# CI/CD Plan - CS320 Contact Service

This document tracks how we will harden the GitHub Actions workflow from a simple compile/test run into a production-grade pipeline. Follow the phases below; check items off as they land.

## Phase 1 - Baseline CI
1. ✅ Compile `src/main` and `src/test` with JDK 17 using the jars in `lib/`.
2. ✅ Run all JUnit tests through the JUnit Platform ConsoleLauncher.
3. ✅ Upload console output so we can inspect failures.
4. ✅ Fail the workflow immediately when compilation or tests fail (no `|| true` or swallowed exits).
5. ✅ Add a build-status badge to `README.md` once the workflow is live.

## Phase 2 - Developer Productivity
1. ✅ Introduce Maven (preferred) or Gradle so commands become `mvn test` or `./gradlew test`.
2. ✅ Cache the build tool’s dependency directory with `actions/cache` to speed up runs.
3. ✅ Add workflow dispatch support so CI can be triggered manually when needed.

## Phase 3 - Quality Gates
1. ✅ Add static analysis (Checkstyle via Maven plugin). Fail the build on violations.
2. ✅ Add JaCoCo coverage reporting with a minimum threshold (e.g., 80%). Fail when coverage drops.
3. ✅ Upload test and coverage reports as workflow artifacts for easy download.
4. ✅ SpotBugs runs in `mvn verify` via `spotbugs-maven-plugin` (4.9.8.1) with `failOnError=true`; see `pom.xml` for the configuration used in CI.

## Phase 4 - Cross-Version Confidence
1. ✅ Expand the job matrix to JDK 17 and JDK 21 on Ubuntu.
2. ✅ (Optional) Add a Windows runner to ensure developer parity across OSes.
   - Windows uses the `skip-testcontainers` profile: runs service/controller suites against H2 (no Docker) with a reduced JaCoCo gate that excludes container-only paths; mutation still runs. Legacy `getInstance()` suites are tagged `legacy-singleton` and can be run separately with `-Plegacy-singleton` when needed.

## Phase 5 - Release Automation
1. ✅ On tagged releases (`v*`), run `mvn package` and upload the JAR as a release artifact using `actions/upload-artifact`.
2. ✅ Publish release notes automatically from the tag’s body (optional).

## Phase 6 - Security & Maintenance
1. ✅ Enable Dependabot for Maven dependency updates once Maven is in place.
2. ✅ Add CodeQL analysis for Java to scan for security issues (workflow provided by GitHub). 
   (Note: the SARIF upload requires Code Scanning to be enabled; for a private repo you may need to make it public or enable GitHub Advanced Security for alerts to appear.)
3. ☐ Configure branch protection so `main` requires the CI workflow to pass before merging.
   (Manual GitHub setting: Settings → Branches → Add rule, require “Java CI” and “CodeQL” checks.)

## Phase 7 - Advanced Enhancements
1. ✅ Integrate OWASP Dependency Check (or similar) to scan third-party jars for CVEs.
2. ✅ Experiment with parallel test execution (Surefire now runs tests in parallel classes).
3. ✅ Add container-based testing (Dockerized Maven run mirrors prod environment).
4. ✅ Introduce mutation testing (PITest) to prove the unit tests detect injected defects.
5. ✅ Revisit workflow documentation in `README.md` once enhancements land and include the build badge.
6. ✅ Publish per-run QA summaries in the GitHub Actions job summary (tests, JaCoCo, PITest, Dependency-Check) via `scripts/ci_metrics_summary.py`.
7. ✅ Normalize CI flags so Dependency-Check skip/delay settings (`dependency.check.skip`, `nvdApiDelay`) match the Maven configuration and avoid hanging runs when secrets are absent.
8. ✅ Containerized Maven rerun (`container-test`) now uses the `skip-testcontainers` profile (H2 only) because the inner Maven container has no Docker socket; quality gates still run with the reduced JaCoCo scope.
8. ✅ Add mutation-focused tests (+71 tests) targeting boundary conditions and comparison operators to improve mutation kill rate. See ADR-0046.

---

**Note:** Phases 8–10 below are *pipeline* phases (CI/CD maturity). They correspond to *implementation* phases in `docs/REQUIREMENTS.md`:
- CI Phase 8 (ZAP) → REQUIREMENTS.md Phase 5.5
- CI Phase 9 (API fuzzing) → REQUIREMENTS.md Phase 2.5
- CI Phase 10 (auth tests) → REQUIREMENTS.md Phase 5.5

---

## Phase 8 - Dynamic Security Testing ✅
1. ✅ Add OWASP ZAP baseline/API scan in CI against a running test instance (fail on high/critical findings).
   - `.github/workflows/zap-scan.yml` runs both baseline and API scans.
   - Uses PostgreSQL service container for realistic testing.
   - Configurable via `.zap/rules.tsv` to manage alert thresholds.
2. ✅ Publish ZAP reports as artifacts for triage.

## Phase 9 - API Fuzzing ✅
1. ✅ Add Schemathesis in CI against the OpenAPI spec; fail on 5xx or schema violations.
   - `.github/workflows/api-fuzzing.yml` runs Schemathesis after app startup.
   - Exports OpenAPI spec as artifact for ZAP integration in Phase 8.
   - Publishes JUnit XML results and summary to GitHub Actions.
2. ✅ Fuzzing runs on ubuntu-latest with 20-minute timeout to keep CI reasonable.
   - Uses `--max-examples 50` and `--workers 1` for speed.
   - Local testing available via `scripts/api_fuzzing.py`.
3. ✅ Workflow hardened (CodeRabbit review):
   - Explicit `pyyaml` installation for reliable YAML export.
   - Robust `jq`-based health check instead of fragile `grep` JSON parsing.
   - JAR file validation: verifies exactly one JAR in `target/` before startup.

## Phase 10 - Auth/Role Integration Tests (Planned)
1. ☐ Add MockMvc/WebTestClient flows that assert 401/403 for anonymous or unauthorized roles and 2xx for allowed roles.
2. ☐ Ensure token/credential handling is wired into CI (test-only secrets).

## Phase 11 - Docker Packaging in CI ✅
1. ✅ Add `docker-build` job to `.github/workflows/java-ci.yml` (runs after `build-test` succeeds).
2. ✅ Build Docker image using multi-stage `Dockerfile` with layer caching via Docker Buildx.
3. ✅ Push images to GitHub Container Registry (ghcr.io) on main/master push events.
4. ✅ Health check step: Start docker compose stack, wait for `/actuator/health` to return healthy.
5. ✅ Smoke test: Validate health endpoint returns `"status":"UP"`.
6. ✅ Cleanup: Tear down stack with `docker compose down -v` (always runs).
7. ✅ Upload docker build logs as artifact on failure for debugging.
8. ✅ Added `Makefile` with 30+ targets for local dev (build, docker, quality, UI).

Keep this plan updated as each phase lands. When a task completes, we replace the checkbox with ✅ and add links to PRs or workflow runs for traceability.
Once all phases are complete, we summarize the final workflow in `README.md`.

<a id="phase-5-5-cookie-rollout"></a>
## Phase 5.5 Cookie Rollout Checklist
1. ✅ Deploy dual-mode auth build (`v5.5.0-migration`) that issues HttpOnly cookies while still
   honoring legacy bearer tokens.
2. ✅ Add bootstrap script to the SPA that exchanges any `localStorage.auth_token` for a cookie and
   deletes the legacy storage key (Vitest coverage included).
3. ✅ Update backend logging/reporting dashboards to emit `LegacyTokenUsed` metrics for tracking
   migration progress.
4. ☐ Run one-week observation period; confirm legacy usage drops below 1% before disabling headers.
5. ☐ Flip the feature flag that rejects Authorization headers missing CSRF cookies, announce the
   change in release notes, and monitor error budgets.
6. ☐ Post-migration, remove the fallback code paths and delete metrics/alerts tied to legacy tokens.
