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
from pathlib import Path
from typing import Dict, List, Optional


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


def load_dependency_check() -> Optional[Dict[str, int]]:
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
    for dep in dependencies:
        vulns = dep.get("vulnerabilities") or []
        if vulns:
            vulnerable_deps += 1
            vuln_total += len(vulns)
    return {
        "dependencies": dep_count,
        "vulnerable_dependencies": vulnerable_deps,
        "vulnerabilities": vuln_total,
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


def main() -> int:
    summary_lines = [section_header(), "", "| Metric | Result | Details |", "| --- | --- | --- |"]

    tests = load_surefire()
    if tests:
        summary_lines.append(
            format_row(
                "Tests",
                f"{tests['tests']} passing"
                + (
                    f" ({tests['failures']} failures, {tests['errors']} errors, {tests['skipped']} skipped)"
                    if tests["failures"] or tests["errors"] or tests["skipped"]
                    else ""
                ),
                f"Total runtime {tests['time']}s",
            )
        )
    else:
        summary_lines.append(format_row("Tests", "_no data_", "Surefire reports not found."))

    jacoco = load_jacoco()
    if jacoco:
        coverage_text = f"{jacoco['pct']}% {bar(jacoco['pct'])}"
        detail = f"{jacoco['covered']} / {jacoco['total']} lines covered"
        summary_lines.append(format_row("Line coverage (JaCoCo)", coverage_text, detail))
    else:
        summary_lines.append(format_row("Line coverage (JaCoCo)", "_no data_", "Jacoco XML report missing." ))

    pit = load_pitest()
    if pit:
        detail = f"{pit['killed']} killed, {pit['survived']} survived out of {pit['total']} mutations"
        summary_lines.append(format_row("Mutation score (PITest)", f"{pit['pct']}% {bar(pit['pct'])}", detail))
    else:
        summary_lines.append(
            format_row("Mutation score (PITest)", "_no data_", "PITest report not generated (likely skipped).")
        )

    dep = load_dependency_check()
    if dep:
        detail = (
            f"{dep['vulnerable_dependencies']} dependencies with issues "
            f"({dep['vulnerabilities']} vulnerabilities total) "
            f"out of {dep['dependencies']} scanned."
        )
        summary_lines.append(format_row("Dependency-Check", "scan complete", detail))
    else:
        summary_lines.append(
            format_row(
                "Dependency-Check",
                "_not run_",
                "Report missing (probably skipped when `NVD_API_KEY` was not provided).",
            )
        )

    summary_lines.append("")
    summary_lines.append("Artifacts: `target/site/jacoco/`, `target/pit-reports/`, `target/dependency-check-report.*`.")
    summary_lines.append("")

    summary_text = "\n".join(summary_lines) + "\n"

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as handle:
            handle.write(summary_text)
    else:
        print(summary_text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
