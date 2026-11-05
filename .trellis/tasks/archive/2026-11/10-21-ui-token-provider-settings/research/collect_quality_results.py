"""Archive quality evidence; compilation and packaging are not device execution."""
import argparse
from collections import Counter
import hashlib
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
assert not (destination / "summary.json").exists(), "Do not overwrite completed quality evidence."
destination.mkdir(exist_ok=True)
text = args.log.read_text(encoding="utf-8-sig", errors="replace")
shutil.copy2(args.log, destination / args.log.name)
task_lines = [line for line in text.splitlines() if line.startswith("> Task ")]
summary = dict(
    command=".\\gradlew.bat :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain",
    commandExitCode=args.exit_code,
    completedBuildLines=[line for line in text.splitlines() if line.startswith("BUILD ") or "actionable tasks:" in line],
    taskLines=task_lines,
    deviceTestsExecuted=False,
    deviceTestsNote="AndroidTest Kotlin compilation only; no connected test, TalkBack, IME, system-bar or physical-performance run.",
)
if args.exit_code == 0:
    lint = root / "app/build/reports/lint-results-debug.xml"
    prior = root / ".trellis/tasks/archive/2026-11/10-21-ui-token-settings-home/research/quality-final/lint-results-debug.xml"
    assert lint.is_file() and prior.is_file()
    def issues(path):
        result = []
        for issue in ET.parse(path).getroot().findall("issue"):
            files = tuple(sorted(location.get("file", "").replace("\\", "/") for location in issue.findall("location")))
            result.append((issue.get("id"), issue.get("severity"), issue.get("message"), files))
        return result
    current_issues = issues(lint)
    previous_issues = issues(prior)
    added = Counter(current_issues) - Counter(previous_issues)
    removed = Counter(previous_issues) - Counter(current_issues)
    summary["lint"] = dict(severities=dict(Counter(issue[1] for issue in current_issues)),
        addedIssues=[dict(id=issue[0], severity=issue[1], message=issue[2], files=issue[3], count=count) for issue, count in added.items()],
        removedIssues=[dict(id=issue[0], severity=issue[1], message=issue[2], files=issue[3], count=count) for issue, count in removed.items()])
    shutil.copy2(lint, destination / lint.name)
    metadata_path = root / "app/build/outputs/apk/debug/output-metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    output = metadata["elements"][0]
    assert output["versionName"] == "2.2.12-dev" and output["versionCode"] == 20212
    apk = metadata_path.parent / output["outputFile"]
    summary["apk"] = dict(file=apk.relative_to(root).as_posix(), size=apk.stat().st_size,
        sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), versionName=output["versionName"], versionCode=output["versionCode"], published=False)
    shutil.copy2(metadata_path, destination / metadata_path.name)
else:
    summary["failureLines"] = [line for line in text.splitlines() if " FAILED" in line or line.startswith("e: ")]
    summary["lint"] = None
    summary["apk"] = None

sources = ["app/src/main/java/com/example/myapplication/ui/screen/settings/" + name for name in (
    "ProviderSettingsScreen.kt", "ProviderDetailScreen.kt", "ProviderDetailChrome.kt", "ProviderConfigTab.kt",
    "ProviderModelTab.kt", "ProviderModelDialogs.kt", "ProviderTemplateDialog.kt", "SettingsComponents.kt")]
sources.append("app/src/main/java/com/example/myapplication/ui/component/NarraAlertDialog.kt")
summary["sourceSha256"] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in sources} if args.exit_code == 0 else None
(destination / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({key: value for key, value in summary.items() if key not in ("sourceSha256", "taskLines")}, ensure_ascii=False, indent=2))
