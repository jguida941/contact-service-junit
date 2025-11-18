# CI/CD Plan - CS320 Contact Service

This document tracks how we will harden the GitHub Actions workflow from a simple compile/test run into a production-grade pipeline. Follow the phases below; check items off as they land.

## Phase 1 – Baseline CI
1. ✅ Compile `src/main` and `src/test` with JDK 17 using the jars in `lib/`.
2. ✅ Run all JUnit tests through the JUnit Platform ConsoleLauncher.
3. ✅ Upload console output so we can inspect failures.
4. ✅ Fail the workflow immediately when compilation or tests fail (no `|| true` or swallowed exits).
5. ✅ Add a build-status badge to `README.md` once the workflow is live.

## Phase 2 – Developer Productivity
1. ✅ Introduce Maven (preferred) or Gradle so commands become `mvn test` or `./gradlew test`.
2. ✅ Cache the build tool’s dependency directory with `actions/cache` to speed up runs.
3. ✅ Add workflow dispatch support so CI can be triggered manually when needed.

## Phase 3 – Quality Gates
1. ✅ Add static analysis (Checkstyle via Maven plugin). Fail the build on violations.
2. ✅ Add JaCoCo coverage reporting with a minimum threshold (e.g., 80%). Fail when coverage drops.
3. ✅ Upload test and coverage reports as workflow artifacts for easy download.
4. ☐ (Deferred) Reintroduce SpotBugs once it’s stable in the local environment (blocked by the local Java/SpotBugs launcher issue). (Phase skipped for now.)

## Phase 4 – Cross-Version Confidence
1. ✅ Expand the job matrix to JDK 17 and JDK 21 on Ubuntu.
2. ✅ (Optional) Add a Windows runner to ensure developer parity across OSes.

## Phase 5 – Release Automation
1. ✅ On tagged releases (`v*`), run `mvn package` and upload the JAR as a release artifact using `actions/upload-artifact`.
2. ✅ Publish release notes automatically from the tag’s body (optional).

## Phase 6 – Security & Maintenance
1. ✅ Enable Dependabot for Maven dependency updates once Maven is in place.
2. ✅ Add CodeQL analysis for Java to scan for security issues (workflow provided by GitHub). (Note: the SARIF upload requires Code Scanning to be enabled; for a private repo you may need to make it public or enable GitHub Advanced Security for alerts to appear.)
3. ☐ Configure branch protection so `main` requires the CI workflow to pass before merging. (Manual GitHub setting: Settings → Branches → Add rule, require “Java CI” and “CodeQL” checks.)

## Phase 7 – Advanced Enhancements
1. ✅ Integrate OWASP Dependency Check (or similar) to scan third-party jars for CVEs.
2. ✅ Experiment with parallel test execution (Surefire now runs tests in parallel classes).
3. ✅ Add container-based testing (Dockerized Maven run mirrors prod environment).
4. ✅ Introduce mutation testing (PITest) to prove the unit tests detect injected defects.
5. ✅ Revisit workflow documentation in `README.md` once enhancements land and include the build badge.
6. ✅ Publish per-run QA summaries in the GitHub Actions job summary (tests, JaCoCo, PITest, Dependency-Check) via `scripts/ci_metrics_summary.py`.

Keep this plan updated as each phase lands. When a task completes, replace the checkbox with ✅ and add links to PRs or workflow runs for traceability. Once all phases are complete, summarize the final workflow in `README.md`.
