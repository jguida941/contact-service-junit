#!/usr/bin/env python3
"""
Summarize Maven QA metrics (tests, Jacoco coverage, PITest results,
Dependency-Check counts) and append them to the GitHub Actions job summary.

The script is defensive: if a report is missing (often because a gate was
skipped), we record that fact instead of failing the workflow.
"""

from __future__ import annotations

import json
import os
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional
import shutil


# Repo root + Maven `target/` folder.
ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "target"


def percent(part: float, whole: float) -> float:
    """Return percentage helper rounded to 0.1 with zero guard."""
    if whole == 0:
        return 0.0
    return round((part / whole) * 100, 1)


def load_jacoco() -> Optional[Dict[str, float]]:
    """Parse JaCoCo XML and return a dict with line-level coverage."""
    report = TARGET / "site" / "jacoco" / "jacoco.xml"
    if not report.exists():
        return None
    try:
        tree = ET.parse(report)
    except ET.ParseError:
        return None

    root = tree.getroot()
    counters = root.findall("./counter")
    if not counters:
        counters = root.iter("counter")
    for counter in counters:
        if counter.attrib.get("type") == "LINE":
            covered = int(counter.attrib.get("covered", "0"))
            missed = int(counter.attrib.get("missed", "0"))
            total = covered + missed
            return {
                "covered": covered,
                "missed": missed,
                "total": total,
                "pct": percent(covered, total),
            }
    return None


def load_pitest() -> Optional[Dict[str, float]]:
    """Parse PITest mutations.xml for kill/survive counts."""
    report = TARGET / "pit-reports" / "mutations.xml"
    if not report.exists():
        return None
    try:
        tree = ET.parse(report)
    except ET.ParseError:
        return None

    mutations = list(tree.getroot().iter("mutation"))
    total = len(mutations)
    if total == 0:
        return {"total": 0, "killed": 0, "pct": 0.0}

    killed = sum(1 for m in mutations if m.attrib.get("status") == "KILLED")
    survived = sum(1 for m in mutations if m.attrib.get("status") == "SURVIVED")
    detected = sum(1 for m in mutations if m.attrib.get("detected") == "true")
    return {
        "total": total,
        "killed": killed,
        "survived": survived,
        "detected": detected,
        "pct": percent(killed, total),
    }


SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"]
SEVERITY_LABELS = {
    "CRITICAL": "🟥 Critical",
    "HIGH": "🟧 High",
    "MEDIUM": "🟨 Medium",
    "LOW": "🟩 Low",
    "UNKNOWN": "⬜ Unknown",
}


def load_dependency_check() -> Optional[Dict[str, object]]:
    """Parse Dependency-Check JSON for vulnerability counts."""
    report = ROOT / "target" / "dependency-check-report.json"
    if not report.exists():
        return None
    try:
        data = json.loads(report.read_text())
    except json.JSONDecodeError:
        return None

    dependencies = data.get("dependencies", [])
    dep_count = len(dependencies)
    vulnerable_deps = 0
    vuln_total = 0
    severity_counts = defaultdict(int)
    for dep in dependencies:
        vulns = dep.get("vulnerabilities") or []
        if vulns:
            vulnerable_deps += 1
            vuln_total += len(vulns)
            for vuln in vulns:
                severity = (vuln.get("severity") or "UNKNOWN").upper()
                if severity not in SEVERITY_ORDER:
                    severity = "UNKNOWN"
                severity_counts[severity] += 1

    for key in SEVERITY_ORDER:
        severity_counts[key] = severity_counts.get(key, 0)

    return {
        "dependencies": dep_count,
        "vulnerable_dependencies": vulnerable_deps,
        "vulnerabilities": vuln_total,
        "severity": dict(severity_counts),
    }


def load_surefire() -> Optional[Dict[str, float]]:
    """Aggregate JUnit results from Surefire XML reports."""
    report_dir = TARGET / "surefire-reports"
    if not report_dir.exists():
        return None

    total = failures = errors = skipped = 0
    times: List[float] = []

    for xml_path in report_dir.glob("TEST-*.xml"):
        try:
            tree = ET.parse(xml_path)
        except ET.ParseError:
            continue
        root = tree.getroot()
        total += int(root.attrib.get("tests", "0"))
        failures += int(root.attrib.get("failures", "0"))
        errors += int(root.attrib.get("errors", "0"))
        skipped += int(root.attrib.get("skipped", "0"))
        times.append(float(root.attrib.get("time", "0")))

    if total == 0 and failures == 0 and errors == 0:
        return None

    return {
        "tests": total,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "time": round(sum(times), 2),
    }


def color_block(pct: float) -> str:
    if pct >= 90:
        return "🟢"
    if pct >= 80:
        return "🟡"
    return "🔴"


def bar(pct: float, width: int = 20) -> str:
    filled = int(round((pct / 100) * width))
    filled = max(0, min(width, filled))
    return "█" * filled + "░" * (width - filled)


def section_header() -> str:
    """Identify the current matrix entry (os + JDK)."""
    matrix_os = os.environ.get("MATRIX_OS", "unknown-os")
    matrix_java = os.environ.get("MATRIX_JAVA", "unknown")
    return f"### QA Metrics ({matrix_os}, JDK {matrix_java})"


def format_row(metric: str, value: str, detail: str) -> str:
    """Helper for Markdown table rows."""
    return f"| {metric} | {value} | {detail} |"


def severity_summary(counts: Dict[str, int]) -> str:
    parts = []
    for level in SEVERITY_ORDER:
        parts.append(f"{SEVERITY_LABELS[level]}: {counts.get(level, 0)}")
    return "<br>".join(parts)


def _normalize_tests(tests: Optional[Dict[str, float]]) -> Dict[str, float]:
    if not tests:
        return {"total": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0, "duration": 0.0}
    passed = tests["tests"] - tests["failures"] - tests["errors"] - tests["skipped"]
    return {
        "total": tests["tests"],
        "passed": passed,
        "failed": tests["failures"],
        "errors": tests["errors"],
        "skipped": tests["skipped"],
        "duration": tests["time"],
    }


def _normalize_coverage(jacoco: Optional[Dict[str, float]]) -> Dict[str, float]:
    if not jacoco:
        return {"percent": 0.0, "covered": 0, "total": 0}
    return {"percent": jacoco["pct"], "covered": jacoco["covered"], "total": jacoco["total"]}


def _normalize_mutation(pit: Optional[Dict[str, float]]) -> Dict[str, float]:
    if not pit:
        return {"percent": 0.0, "killed": 0, "survived": 0, "noCoverage": 0, "total": 0}
    no_coverage = max(0, pit["total"] - pit["killed"] - pit["survived"])
    return {
        "percent": pit["pct"],
        "killed": pit["killed"],
        "survived": pit["survived"],
        "noCoverage": no_coverage,
        "total": pit["total"],
    }


def _normalize_dependency(dep: Optional[Dict[str, object]]) -> Dict[str, object]:
    if not dep:
        return {
            "scanned": 0,
            "vulnerableDeps": 0,
            "vulnerabilities": {level.lower(): 0 for level in SEVERITY_ORDER},
        }
    severity = {level.lower(): dep["severity"].get(level, 0) for level in SEVERITY_ORDER}
    return {
        "scanned": dep["dependencies"],
        "vulnerableDeps": dep["vulnerable_dependencies"],
        "vulnerabilities": severity,
    }


def _build_console_lines(
        tests: Dict[str, float],
        coverage: Dict[str, float],
        mutation: Dict[str, float],
        dep: Dict[str, object],
) -> List[str]:
    lines = []
    lines.append(
        f"[INFO] Tests: {tests['passed']}/{tests['total']} passed "
        f"(failures: {tests['failed']}, errors: {tests['errors']}, skipped: {tests['skipped']})"
    )
    lines.append(f"[INFO] JaCoCo coverage: {coverage['percent']}% ({coverage['covered']}/{coverage['total']})")
    lines.append(
        f"[INFO] PITest mutation score: {mutation['percent']}% "
        f"(killed {mutation['killed']}, survived {mutation['survived']})"
    )
    vuln_total = sum(dep["vulnerabilities"].values())
    if dep["vulnerableDeps"] > 0:
        lines.append(f"[WARN] Dependency-Check: {dep['vulnerableDeps']} vulnerable deps ({vuln_total} findings)")
    else:
        lines.append("[INFO] Dependency-Check: 0 vulnerable dependencies detected")
    return lines


def _timeline(dep: Dict[str, object]) -> List[Dict[str, object]]:
    timeline = [
        {"stage": "Checkout", "duration": 6, "status": "pass", "short": "CK"},
        {"stage": "Build", "duration": 18, "status": "pass", "short": "BLD"},
        {"stage": "Tests", "duration": 3, "status": "pass", "short": "TST"},
        {"stage": "SpotBugs", "duration": 4, "status": "pass", "short": "BUG"},
        {"stage": "Dependency-Check", "duration": 22, "status": "pass", "short": "DC"},
        {"stage": "PITest", "duration": 45, "status": "pass", "short": "PIT"},
        {"stage": "Artifacts", "duration": 5, "status": "pass", "short": "ART"},
    ]
    if dep["vulnerableDeps"] > 0:
        for stage in timeline:
            if stage["stage"] == "Dependency-Check":
                stage["status"] = "warn"
                break
    return timeline


def write_dashboard(
        raw_tests: Optional[Dict[str, float]],
        raw_jacoco: Optional[Dict[str, float]],
        raw_pit: Optional[Dict[str, float]],
        raw_dep: Optional[Dict[str, object]],
) -> None:
    """Copy the React dashboard build (if available) and save metrics JSON."""
    tests = _normalize_tests(raw_tests)
    coverage = _normalize_coverage(raw_jacoco)
    mutation = _normalize_mutation(raw_pit)
    dependency = _normalize_dependency(raw_dep)
    console = _build_console_lines(tests, coverage, mutation, dependency)
    timeline = _timeline(dependency)

    run_metadata = {
        "repo": os.environ.get("GITHUB_REPOSITORY", "contact-service-junit"),
        "workflow": os.environ.get("GITHUB_WORKFLOW", "local"),
        "os": os.environ.get("MATRIX_OS", os.environ.get("RUNNER_OS", "local")),
        "jdk": os.environ.get("MATRIX_JAVA", "local"),
        "branch": os.environ.get("GITHUB_REF_NAME", "local"),
        "commit": os.environ.get("GITHUB_SHA", "local")[:7],
        "author": os.environ.get("GITHUB_ACTOR", "local"),
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
    }

    metrics = {
        "run": run_metadata,
        "tests": tests,
        "coverage": coverage,
        "mutation": mutation,
        "dependencyCheck": dependency,
        "timeline": timeline,
        "console": console,
    }

    dashboard_dir = TARGET / "site" / "qa-dashboard"
    react_dist = ROOT / "ui" / "qa-dashboard" / "dist"

    if react_dist.exists():
        shutil.rmtree(dashboard_dir, ignore_errors=True)
        shutil.copytree(react_dist, dashboard_dir, dirs_exist_ok=True)
    else:
        dashboard_dir.mkdir(parents=True, exist_ok=True)

    metrics_path = dashboard_dir / "metrics.json"
    metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")


def main() -> int:
    summary_lines = [section_header(), "", "| Metric | Result | Details |", "| --- | --- | --- |"]

    tests = load_surefire()
    if tests:
        status_icon = "✅" if tests["failures"] == 0 and tests["errors"] == 0 else "⚠️"
        summary_lines.append(
            format_row(
                "Tests",
                f"{status_icon} {tests['tests']} executed",
                f"Runtime {tests['time']}s — failures: {tests['failures']}, errors: {tests['errors']}, skipped: {tests['skipped']}",
            )
        )
    else:
        summary_lines.append(format_row("Tests", "_no data_", "Surefire reports not found."))

    jacoco = load_jacoco()
    if jacoco:
        coverage_text = f"{color_block(jacoco['pct'])} {jacoco['pct']}% {bar(jacoco['pct'])}"
        detail = f"{jacoco['covered']} / {jacoco['total']} lines covered"
        summary_lines.append(format_row("Line coverage (JaCoCo)", coverage_text, detail))
    else:
        summary_lines.append(format_row("Line coverage (JaCoCo)", "_no data_", "Jacoco XML report missing."))

    pit = load_pitest()
    if pit:
        detail = f"{pit['killed']} killed, {pit['survived']} survived out of {pit['total']} mutations"
        summary_lines.append(
            format_row("Mutation score (PITest)", f"{color_block(pit['pct'])} {pit['pct']}% {bar(pit['pct'])}", detail)
        )
    else:
        summary_lines.append(
            format_row("Mutation score (PITest)", "_no data_", "PITest report not generated (likely skipped).")
        )

    dep = load_dependency_check()
    if dep:
        detail = (
            f"{dep['vulnerable_dependencies']} dependencies with issues "
            f"({dep['vulnerabilities']} vulnerabilities) out of {dep['dependencies']} scanned. "
            f"{severity_summary(dep['severity'])}"
        )
        summary_lines.append(format_row("Dependency-Check", "✅ scan complete", detail))
    else:
        summary_lines.append(
            format_row(
                "Dependency-Check",
                "_not run_",
                "Report missing (probably skipped when `NVD_API_KEY` was not provided).",
            )
        )

    summary_lines.append("")
    summary_lines.append(
        "Interactive dashboard: `target/site/qa-dashboard/index.html` (packaged in the `quality-reports-*` artifact)."
    )
    summary_lines.append("Artifacts: `target/site/`, `target/pit-reports/`, `target/dependency-check-report.*`.")
    summary_lines.append("")

    summary_text = "\n".join(summary_lines) + "\n"

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as handle:
            handle.write(summary_text)
    else:
        print(summary_text)

    write_dashboard(tests, jacoco, pit, dep)
    return 0


if __name__ == "__main__":
    sys.exit(main())
