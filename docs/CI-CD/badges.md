# CI Badge Automation (JaCoCo, PITest, SpotBugs, OWASP DC)

## Overview
- The README shows four live shields.io badges:
  - JaCoCo line coverage (`badges/jacoco.json`)
  - PITest mutation score (`badges/mutation.json`)
  - SpotBugs findings (`badges/spotbugs.json`)
  - OWASP Dependency-Check findings (`badges/dependency.json`)
- These JSON files live under `badges/` in the repo and are published by the `scripts/ci_metrics_summary.py` helper.

## How the helper works
1. Run the full QA suite:
   ```bash
   mvn verify
   ```
2. Run the summary script with badge updates enabled:
   ```bash
   UPDATE_BADGES=true python scripts/ci_metrics_summary.py
   ```
3. Commit the updated JSON files inside `badges/`.

The summary script parses the JaCoCo XML report, PITest mutations report, SpotBugs XML report, and Dependency-Check JSON. It writes the latest metrics into the badge JSON files so shields.io can render fresh badges every time they’re run.

## Optional customization
- Change the badge output directory by setting `BADGE_OUTPUT_DIR=/path/to/dir`.
- Disable badge regeneration by leaving `UPDATE_BADGES` unset or set to `false`.
- Colors:
  - JaCoCo/PITest badge colors are based on the percentage (>=90% green, >=75% yellow, >=60% orange, otherwise red).
  - SpotBugs and OWASP DC badges display “clean” in green when zero findings exist or `{n} issues`/`{n} vulns` when counts are nonzero.
