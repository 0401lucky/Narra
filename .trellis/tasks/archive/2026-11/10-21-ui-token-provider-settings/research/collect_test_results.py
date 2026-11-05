"""Preserve a finished JVM run before another selector overwrites Gradle reports."""
import argparse
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET


parser = argparse.ArgumentParser()
parser.add_argument("name")
parser.add_argument("--exit-code", type=int, required=True)
parser.add_argument("--log", type=Path, required=True)
parser.add_argument("--command", required=True)
args = parser.parse_args()
research = Path(__file__).resolve().parent
root = next(parent for parent in research.parents if (parent / "app/build.gradle").is_file())
destination = (research / args.name).resolve()
assert destination.is_relative_to(research) and destination != research
assert not (destination / "summary.json").exists(), "Do not overwrite completed evidence."
destination.mkdir(exist_ok=True)
totals = dict(tests=0, failures=0, errors=0, skipped=0)
failures = []
reports = list((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
assert reports, "No JUnit report was produced."
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, 0))
    for test in suite.findall("testcase"):
        for failure in test.findall("failure") + test.findall("error"):
            message = failure.get("message", "")
            failures.append(dict(suite=suite.get("name"), test=test.get("name"), message=message,
                category="missing-baseline" if "original file" in message or "缺少已登记的基准 PNG" in message else "behavior-or-infrastructure"))
    shutil.copy2(report, destination / report.name)
shutil.copy2(args.log, destination / args.log.name)
summary = dict(command=args.command, commandExitCode=args.exit_code, suites=len(reports),
    **totals, failureDetails=failures)
(destination / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({key: value for key, value in summary.items() if key != "failureDetails"}, ensure_ascii=False, indent=2))

