const sampleMetrics = {
  run: {
    repo: "contact-service-junit",
    workflow: "Verify",
    os: "ubuntu-latest",
    jdk: "21",
    branch: "master",
    commit: "2c212b6",
    author: "jguida941",
    timestamp: "2025-11-18 21:34 UTC",
  },
  tests: {
    total: 47,
    passed: 47,
    failed: 0,
    errors: 0,
    skipped: 0,
    duration: 0.14,
  },
  coverage: {
    percent: 100,
    covered: 69,
    total: 69,
    history: [96, 97.5, 98.4, 100],
  },
  mutation: {
    percent: 84.2,
    killed: 160,
    survived: 24,
    noCoverage: 6,
    total: 190,
  },
  spotbugs: {
    issues: 0,
    lastRun: "2025-11-18 21:34 UTC",
  },
  dependencyCheck: {
    scanned: 38,
    vulnerableDeps: 1,
    vulnerabilities: {
      critical: 0,
      high: 1,
      medium: 2,
      low: 0,
    },
    highlights: [
      {
        cve: "CVE-2024-22201",
        cvss: 7.5,
        component: "commons-io-2.15.0.jar",
      },
    ],
  },
  timeline: [
    { stage: "Checkout", duration: 6, status: "pass", short: "CK" },
    { stage: "Build", duration: 18, status: "pass", short: "BLD" },
    { stage: "Tests", duration: 3, status: "pass", short: "TST" },
    { stage: "SpotBugs", duration: 4, status: "pass", short: "BUG" },
    { stage: "Dependency-Check", duration: 22, status: "warn", short: "DC" },
    { stage: "PITest", duration: 45, status: "pass", short: "PIT" },
    { stage: "Artifacts", duration: 5, status: "pass", short: "ART" },
  ],
  console: [
    "[INFO] Running mvn -B verify",
    "[INFO] Surefire: 47 tests, 0 failures, 0 errors, 0 skipped",
    "[INFO] JaCoCo coverage: 100% (69/69)",
    "[INFO] SpotBugs: 0 findings",
    "[WARN] Dependency-Check: 1 vulnerable dependency detected",
    "[INFO] PITest mutation score: 84.2%",
    "[INFO] Build succeeded in 108s",
  ],
};

export default sampleMetrics;
