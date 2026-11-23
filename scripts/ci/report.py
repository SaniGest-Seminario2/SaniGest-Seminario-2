#!/usr/bin/env python3
"""Validate required Maven XML reports and summarize the results for GitHub Actions."""

import argparse
from decimal import Decimal
import html
import math
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

COUNTS = ("tests", "failures", "errors", "skipped")
PHASES = {
    "unit": "Unit tests",
    "gateway": "HTTP integration tests",
    "smoke": "Smoke tests",
    "regression": "Regression tests",
    "coverage": "Instruction coverage",
}


def plain(value):
    return re.sub(r"[\x00-\x1f\x7f]+", " ", str(value)).strip()[:350]


def markdown(value):
    escaped = html.escape(plain(value))
    for char in "|`[]":
        escaped = escaped.replace(char, f"&#{ord(char)};")
    return escaped


def annotation(message):
    print("::error::" + plain(message).replace("%", "%25"))


def test_results(module, report_dir):
    totals = dict.fromkeys(COUNTS, 0)
    durations = []
    problems = []
    reports = sorted((module / "target" / report_dir).glob("TEST-*.xml"))
    for path in reports:
        try:
            suite = ET.parse(path).getroot()
            if suite.tag != "testsuite":
                raise ValueError("expected a testsuite root")
            counts = {key: int(suite.attrib[key]) for key in COUNTS}
            if min(counts.values()) < 0 or sum(counts[key] for key in COUNTS[1:]) > counts["tests"]:
                raise ValueError("invalid test counts")
            cases = suite.findall("testcase")
            if len(cases) != counts["tests"] or any(
                sum(case.find(element) is not None for case in cases) != counts[key]
                for key, element in (("failures", "failure"), ("errors", "error"), ("skipped", "skipped"))
            ):
                raise ValueError("test case entries differ from suite totals")
            duration = float(suite.attrib["time"]) if "time" in suite.attrib else None
            if duration is not None and (not math.isfinite(duration) or duration < 0):
                raise ValueError("invalid test duration")
        except (ET.ParseError, OSError, KeyError, ValueError) as error:
            problems.append(f"{path}: invalid test report ({error})")
            continue
        for key, count in counts.items():
            totals[key] += count
        durations.append(duration)
        for case in cases:
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                test = f"{case.get('classname', suite.get('name', '?'))}.{case.get('name', '?')}"
                reason = (problem.get("message") or problem.get("type") or "See report").strip()
                problems.append(f"{module.name} / {test}: {reason[:180]} ({path.name})")
    seconds = sum(durations) if durations and None not in durations else None
    return reports, len(durations), totals, seconds, problems


def missing_test_report(module, report_dir, outcome):
    directory = module / "target" / report_dir
    summary = directory / "failsafe-summary.xml"
    if summary.is_file():
        try:
            completed = int(ET.parse(summary).getroot().findtext("completed", ""))
        except (ET.ParseError, OSError, ValueError):
            return "Invalid Failsafe summary and no suite XML"
        if completed > 0:
            return "Failsafe ran tests but suite XML is missing"
    if any(directory.glob("*.txt")):
        return "Test text reports exist but suite XML is missing"
    if outcome == "failure":
        return "Maven failed before producing suite reports"
    return "No tests discovered (no suite reports)"


def coverage_result(module):
    path = module / "target/site/jacoco/jacoco.xml"
    root = ET.parse(path).getroot()
    if root.tag != "report":
        raise ValueError("expected a JaCoCo report root")
    counter = root.find("counter[@type='INSTRUCTION']")
    if counter is None:
        raise ValueError("missing instruction counter")
    covered, missed = (int(counter.attrib[key]) for key in ("covered", "missed"))
    if min(covered, missed) < 0 or covered + missed == 0:
        raise ValueError("invalid instruction counts")
    return covered, covered + missed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scope")
    parser.add_argument("phase", choices=PHASES)
    parser.add_argument("outcome", choices=("success", "failure", "skipped", "cancelled"))
    parser.add_argument("artifact")
    parser.add_argument("modules", type=Path, nargs="+")
    args = parser.parse_args()
    lines = [f"### {markdown(args.scope)} / {PHASES[args.phase]}", ""]
    problems = []
    if args.outcome in ("skipped", "cancelled"):
        lines += [f"Stage {args.outcome}. See the preceding steps for details.", ""]
    elif args.phase == "coverage":
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        threshold = ET.parse("pom.xml").findtext(
            ".//m:execution[m:id='jacoco-check']//m:minimum", namespaces=ns
        )
        minimum = Decimal(threshold) * 100
        minimum_label = f"{minimum.normalize():f}%"
        lines += ["| Module | Instructions covered | Minimum |", "|---|---:|---:|"]
        for module in args.modules:
            try:
                covered, total = coverage_result(module)
                actual = 100 * covered / total
                lines.append(f"| {module.name} | {actual:.2f}% ({covered}/{total}) | {minimum_label} |")
                if covered * 100 < minimum * total:
                    problems.append(f"{module.name}: instruction coverage {actual:.2f}% (minimum {minimum_label})")
            except FileNotFoundError:
                problems.append(f"{module.name}: JaCoCo XML missing ({args.outcome} stage)")
                lines.append(f"| {module.name} | Missing report | {minimum_label} |")
            except (ET.ParseError, OSError, KeyError, ValueError) as error:
                problems.append(f"{module.name}: invalid JaCoCo XML ({error})")
                lines.append(f"| {module.name} | Invalid report | {minimum_label} |")
        if args.scope == "Maven reactor":
            lines += ["", "Gateway unit coverage is currently not applicable. Its HTTP integration tests run separately."]
    else:
        report_dir = "surefire-reports" if args.phase == "unit" else "failsafe-reports"
        lines += [
            "| Module | Executed | Passed | Failures | Errors | Skipped | Suite time |",
            "|---|---:|---:|---:|---:|---:|---:|",
        ]
        for module in args.modules:
            reports, valid, counts, seconds, issues = test_results(module, report_dir)
            problems.extend(issues)
            if not reports:
                reason = missing_test_report(module, report_dir, args.outcome)
                problems.append(f"{module.name}: {reason}")
                lines.append(f"| {module.name} | {reason} | — | — | — | — | — |")
                continue
            if not valid:
                lines.append(f"| {module.name} | Invalid reports | — | — | — | — | — |")
                continue
            tests, failures, errors, skipped = counts.values()
            passed = tests - failures - errors - skipped
            if tests == skipped:
                problems.append(f"{module.name}: no tests executed ({skipped} skipped)")
            duration = f"{seconds:.2f} s" if seconds is not None else "—"
            lines.append(f"| {module.name} | {tests - skipped} | {passed} | {failures} | {errors} | {skipped} | {duration} |")
        if args.scope == "Maven reactor":
            lines += ["", "Gateway unit tests are currently not applicable. Gateway integration is reported separately."]

    status = args.outcome.title()
    if args.outcome == "success":
        status = "Failed" if problems else "Passed"
    elif args.outcome == "failure":
        status = "Failed (Maven stage)"
    lines += ["", f"**Result:** {status}", "", f"**Reports artifact:** {markdown(args.artifact)} (when files exist).", ""]
    if problems:
        for problem in problems[:10]:
            annotation(problem)
            lines.append(f"- {markdown(problem)}")
        if len(problems) > 10:
            lines.append(f"- {len(problems) - 10} more issues in the step log or reports.")
        lines.append("")
    summary = "\n".join(lines) + "\n"
    try:
        destination = os.environ.get("GITHUB_STEP_SUMMARY")
        if destination:
            with open(destination, "a", encoding="utf-8") as stream:
                stream.write(summary)
        else:
            print(summary)
    except OSError:
        print("::warning::Could not write the Maven report summary.", file=sys.stderr)
    return int(bool(problems) and args.outcome == "success")


if __name__ == "__main__":
    sys.exit(main())
