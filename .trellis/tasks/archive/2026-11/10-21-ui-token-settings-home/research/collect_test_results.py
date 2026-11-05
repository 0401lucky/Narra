"""Archive the completed Gradle JVM result set before the next targeted run replaces it."""
import argparse
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("name")
parser.add_argument("--exit-code", type=int, required=True)
parser.add_argument("--log", type=Path, required=True)
args = parser.parse_args()
research = Path(__file__).resolve().parent
root = next(parent for parent in research.parents if (parent / "app/build.gradle").is_file())
destination = (research / args.name).resolve()
assert destination.is_relative_to(research) and destination != research
assert not (destination / "summary.json").exists(), "Do not overwrite completed validation evidence."
destination.mkdir(exist_ok=True)
totals = dict(tests=0, failures=0, errors=0, skipped=0)
failures = []
reports = list((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
assert reports, "No JUnit results were produced."
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, 0))
    for test in suite.findall("testcase"):
        for failure in test.findall("failure") + test.findall("error"):
            failures.append(dict(suite=suite.get("name"), test=test.get("name"), message=failure.get("message")))
    shutil.copy2(report, destination / report.name)
shutil.copy2(args.log, destination / args.log.name)
summary = dict(commandExitCode=args.exit_code, suites=len(reports), **totals, failureDetails=failures)
(destination / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(summary, ensure_ascii=False, indent=2))
