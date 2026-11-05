"""冻结隔离worktree的真实E11 actual；只写稳定阶段证据，不改接纳基准。"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import struct
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("phase", choices=("before", "after", "after-repeat"))
parser.add_argument("--log", type=Path, required=True)
args = parser.parse_args()
stable = Path(__file__).resolve().parent
research = stable.parent
main = next(p for p in stable.parents if (p / "app/build.gradle").is_file())
isolated = Path("D:/p11-capture-stability/20261105")
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
accepted = {line.split("  ", 1)[1]: line.split("  ", 1)[0]
            for line in (research / "accepted-png-hashes-20261105.txt").read_text().splitlines()}
assert len(accepted) == 158
assert {p.relative_to(main).as_posix(): sha(p) for p in (main / "app/src/test/snapshots").rglob("*.png")} == accepted
old = {line.split("  ", 1)[1]: line.split("  ", 1)[0]
       for line in (research / "accepted-png-hashes-before-p11.txt").read_text().splitlines()}
assert len(old) == 126
assert {p.relative_to(isolated).as_posix(): sha(p) for p in (isolated / "app/src/test/snapshots").rglob("*.png")} == old
quality = json.loads((research / "quality-verified/summary.json").read_text(encoding="utf-8"))
for name, digest in quality["sourceSha256"].items():
    assert sha(main / name) == digest, name
original = json.loads((research / "after/manifest.json").read_text(encoding="utf-8"))
expected = {row["id"]: row for row in original["cases"]}
results = isolated / "app/build/test-results/testDebugUnitTest"
paths = list(results.glob("TEST-*.xml"))
suites = [ET.parse(path).getroot() for path in paths]
totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ("tests", "failures", "errors", "skipped")}
assert totals == dict(tests=33, failures=33, errors=0, skipped=0), totals
capture_suite = next(s for s in suites if s.get("name").endswith("ProviderSettingsScreenshotE11Test"))
started = datetime.fromisoformat(capture_suite.get("timestamp").replace("Z", "+00:00")).timestamp()
for suite in suites:
    for case in suite.findall("testcase"):
        failure = case.find("failure")
        assert failure is not None
        message = failure.get("message", "")
        assert "original file" in message or "缺少已登记" in message, message
destination = stable / args.phase
assert not (destination / "manifest.json").exists()
destination.mkdir(exist_ok=True)
output = isolated / "app/build/outputs/roborazzi"
records = []
for name, old in sorted(expected.items()):
    actual = output / (name + "_actual.png")
    assert actual.exists() and actual.stat().st_mtime >= started - 2, name
    content = actual.read_bytes()
    assert content[:8] == b"\x89PNG\r\n\x1a\n"
    dimensions = list(struct.unpack(">II", content[16:24]))
    semantics = output / (name + "-semantics.xml")
    measurement = output / (name + "-measurements.txt")
    window = output / (name + "-window.txt")
    for path in (actual, semantics, measurement):
        assert path.is_file() and path.stat().st_mtime >= started - 2
        shutil.copy2(path, destination / path.name)
    xml = ET.parse(semantics).getroot()
    assert xml.get("id") == name and float(xml.get("fontScale")) == old["fontScale"]
    is_window = old["target"] == "independent-dialog-root"
    assert window.is_file() == is_window
    if is_window:
        raw_window = window.read_text(encoding="utf-8")
        assert "IsDialog" in raw_window and "screenshot_stage" not in raw_window
        assert window.stat().st_mtime >= started - 2
        shutil.copy2(window, destination / window.name)
    records.append({"id": name, "file": actual.name, "sha256": sha(actual), "size": dimensions,
                    "fontScale": old["fontScale"], "stageDp": old["stageDp"], "target": old["target"],
                    "window": window.read_text(encoding="utf-8").split("Printing")[0] if is_window else None,
                    "measurements": measurement.read_text(encoding="utf-8")})
for path in paths:
    shutil.copy2(path, destination / path.name)
shutil.copy2(args.log, destination / "run.log")
source_hashes = {name: sha(isolated / name) for name in quality["sourceSha256"]}
if args.phase != "before":
    assert source_hashes == quality["sourceSha256"]
test_names = ("ProviderSettingsScreenshotE11Test.kt", "ProviderSettingsRenderTest.kt", "ScreenshotCases.kt")
test_hashes = {"app/src/test/java/com/example/myapplication/ui/screenshot/" + name:
               sha(isolated / "app/src/test/java/com/example/myapplication/ui/screenshot" / name) for name in test_names}
manifest = {"phase": args.phase, "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
            "command": ".\\gradlew.bat :app:verifyRoborazziDebug --tests com.example.myapplication.ui.screenshot.ProviderSettingsScreenshotE11Test --tests com.example.myapplication.ui.screenshot.ScreenshotBaselineGuardTest --console=plain",
            "commandExitCode": 1, "isolatedSourceRoot": str(isolated), "isolatedBaseCommit": "ac22bb271007c814b6ef49b8499858acc815661c",
            "expectedMissingBaselines": 32, "independentWindows": 12,
            "isolatedAcceptedPngsUnchanged": 126, "mainAcceptedPngsUnchanged": 158,
            "sourceSha256": source_hashes, "testSourceSha256": test_hashes, "cases": records,
            "captureCorrection": "E11-only semantic Tab OnClick; real touch behavior tests unchanged; no record or baseline rewrite"}
(destination / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
log = args.log.read_text(encoding="utf-8-sig")
summary = {"command": manifest["command"], "commandExitCode": 1, "suites": len(suites), **totals,
           "actualTaskLines": [line for line in log.splitlines() if line.startswith("> Task")],
           "buildLines": [line for line in log.splitlines() if line.startswith(("BUILD ", "35 actionable"))],
           "failureKind": "32 isolated missing E11 baselines + count guard; no behavior failures"}
(destination / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Frozen {args.phase}: 32 fresh actuals, 12 windows, 33 missing-baseline failures; main 158 PNGs unchanged.")
