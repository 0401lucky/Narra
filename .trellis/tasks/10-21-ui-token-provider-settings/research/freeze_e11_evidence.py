"""Freeze real E11 verify actuals, preconditions and hashes without accepting baselines."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import struct
import xml.etree.ElementTree as ET


parser = argparse.ArgumentParser()
parser.add_argument("phase", choices=("before", "after"))
parser.add_argument("--log", type=Path, required=True)
parser.add_argument("--exit-code", type=int, required=True)
parser.add_argument("--command", required=True)
args = parser.parse_args()
research = Path(__file__).resolve().parent
root = next(parent for parent in research.parents if (parent / "app/build.gradle").is_file())
outputs = root / "app/build/outputs/roborazzi"
results = root / "app/build/test-results/testDebugUnitTest"
destination = research / args.phase
assert args.exit_code == 1, "Unaccepted E11 baselines must remain missing."
assert not (destination / "manifest.json").exists(), "Do not overwrite a frozen phase."
expected_ids = {
    f"E11_list-{theme}-{shade}"
    for theme in ("matcha", "vanilla", "ocean") for shade in ("light", "dark")
} | {
    f"E11_{scene}-{shade}"
    for scene in ("config", "models", "config-dynamic", "list-narrow", "config-save-narrow",
        "models-tail-narrow", "template-dialog", "fetched-sheet", "manual-error-dialog",
        "ability-dialog", "clear-dialog", "protocol-dialog")
    for shade in ("light", "dark")
} | {"E11_list-no-result-light", "E11_models-empty-dark"}
assert len(expected_ids) == 32
suite = ET.parse(results / "TEST-com.example.myapplication.ui.screenshot.ProviderSettingsScreenshotE11Test.xml").getroot()
assert len(suite.findall("testcase")) == 32
observed_ids = {"E11_" + test.get("name").removeprefix("e11_").replace("_", "-") for test in suite.findall("testcase")}
assert observed_ids == expected_ids, observed_ids ^ expected_ids
run_started = datetime.fromisoformat(suite.get("timestamp").replace("Z", "+00:00")).timestamp()
for test in suite.findall("testcase"):
    failure = test.find("failure")
    assert failure is not None and "original file" in failure.get("message", ""), ET.tostring(test).decode()
assert int(suite.get("errors", 0)) == 0 and int(suite.get("skipped", 0)) == 0
accepted = (research / "accepted-png-hashes-before-p11.txt").read_text(encoding="utf-8-sig").splitlines()
assert len(accepted) == 126
assert len(list((root / "app/src/test/snapshots").rglob("*.png"))) == 126
for line in accepted:
    digest, name = line.split("  ", 1)
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == digest, name
actuals = {p.name.removesuffix("_actual.png"): p for p in outputs.glob("E11*_actual.png")}
assert actuals.keys() == expected_ids, actuals.keys() ^ expected_ids
destination.mkdir(exist_ok=True)
records = []
for case_id, actual in sorted(actuals.items()):
    window_case = "-dialog-" in case_id or "-sheet-" in case_id
    narrow = "-narrow-" in case_id or window_case
    data = actual.read_bytes()
    width, height = struct.unpack(">II", data[16:24])
    assert data[:8] == b"\x89PNG\r\n\x1a\n" and len(data) > 1024
    if window_case:
        assert 0 < width <= 640 and 0 < height <= 1280, (case_id, width, height)
    else:
        assert (width, height) == ((640, 1280) if narrow else (720, 1600)), (case_id, width, height)
    semantics = outputs / f"{case_id}-semantics.xml"
    measurements = outputs / f"{case_id}-measurements.txt"
    window = outputs / f"{case_id}-window.txt"
    for evidence in (actual, semantics, measurements):
        assert evidence.is_file(), evidence
        assert evidence.stat().st_mtime >= run_started - 2, f"Stale evidence: {evidence}"
        shutil.copy2(evidence, destination / evidence.name)
    semantics_root = ET.parse(semantics).getroot()
    assert semantics_root.get("id") == case_id
    assert float(semantics_root.get("fontScale")) == (2 if narrow else 1)
    assert window.is_file() == window_case, case_id
    if window_case:
        window_text = window.read_text(encoding="utf-8")
        assert "IsDialog" in window_text and "screenshot_stage" not in window_text, case_id
        assert window.stat().st_mtime >= run_started - 2, window
        shutil.copy2(window, destination / window.name)
    records.append(dict(
        id=case_id, file=actual.name, sha256=hashlib.sha256(data).hexdigest(), size=[width, height],
        fontScale=2 if narrow else 1, stageDp=[320, 640] if narrow else [360, 800],
        target="independent-dialog-root" if window_case else "screenshot_stage",
        window=window.read_text(encoding="utf-8").split("Printing")[0] if window_case else None,
        measurements=measurements.read_text(encoding="utf-8"),
    ))
for report in results.glob("TEST-*.xml"):
    shutil.copy2(report, destination / report.name)
shutil.copy2(args.log, destination / args.log.name)
sources = [
    "app/src/main/java/com/example/myapplication/ui/screen/settings/" + name
    for name in ("ProviderSettingsScreen.kt", "ProviderDetailScreen.kt", "ProviderDetailChrome.kt",
        "ProviderConfigTab.kt", "ProviderModelTab.kt", "ProviderModelDialogs.kt", "ProviderTemplateDialog.kt", "SettingsComponents.kt")
]
shared_dialog = json.loads((research / "shared-dialog-baseline-20261105.json").read_text(encoding="utf-8"))
sources.append(shared_dialog["file"])
tests = ["app/src/test/java/com/example/myapplication/ui/screenshot/" + name
    for name in ("ProviderSettingsRenderTest.kt", "ProviderSettingsScreenshotE11Test.kt", "ScreenshotCases.kt")]
source_hashes = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in sources}
if args.phase == "before":
    initial = json.loads((research / "workspace-start-20261105.json").read_text(encoding="utf-8"))
    expected_sources = initial["source_sha256"] | {shared_dialog["file"]: shared_dialog["sha256"]}
    assert all(expected_sources[name] == digest for name, digest in source_hashes.items()), "Product changed before capture"
(destination / "manifest.json").write_text(json.dumps(dict(
    phase=args.phase, capturedAtUtc=datetime.now(timezone.utc).isoformat(), command=args.command,
    commandExitCode=args.exit_code, expectedMissingBaselines=32, acceptedPngHashesUnchanged=126,
    independentWindows=12, sourceSha256=source_hashes,
    sharedDialogBeforeSource="shared-dialog-baseline-20261105.json",
    testSourceSha256={name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in tests}, cases=records,
), ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Frozen {len(records)} {args.phase} actuals, including 12 independent windows; 126 accepted PNGs unchanged.")
