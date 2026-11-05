"""Freeze E10 verify actuals and real-window evidence without changing accepted PNGs."""
import argparse
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
args = parser.parse_args()
research = Path(__file__).resolve().parent
root = next(parent for parent in research.parents if (parent / "app/build.gradle").is_file())
outputs = root / "app/build/outputs/roborazzi"
results = root / "app/build/test-results/testDebugUnitTest"
destination = research / args.phase
assert args.exit_code == 1, "Unaccepted E10 baselines must remain missing."
assert not (destination / "manifest.json").exists(), "Do not overwrite a frozen evidence phase."
expected_ids = {
    f"E10_home-{theme}-{shade}"
    for theme in ("matcha", "vanilla", "ocean")
    for shade in ("light", "dark")
} | {
    f"E10_{scene}-{shade}"
    for scene in ("home-dynamic", "home-narrow", "theme-sheet", "mode-sheet", "display-sheet")
    for shade in ("light", "dark")
}
suite_path = results / "TEST-com.example.myapplication.ui.screenshot.SettingsHomeScreenshotE10Test.xml"
suite = ET.parse(suite_path).getroot()
assert len(suite.findall("testcase")) == 16
for test in suite.findall("testcase"):
    failure = test.find("failure")
    assert failure is not None and "original file" in failure.get("message", ""), ET.tostring(test).decode()
assert int(suite.get("errors", 0)) == 0 and int(suite.get("skipped", 0)) == 0

accepted = (research / "accepted-png-hashes-before-p10.txt").read_text(encoding="utf-8-sig").splitlines()
assert len(accepted) == 110
for line in accepted:
    expected_hash, name = line.split("  ", 1)
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == expected_hash, name

actuals = {item.name.removesuffix("_actual.png"): item for item in outputs.glob("E10*_actual.png")}
assert actuals.keys() == expected_ids, (actuals.keys() ^ expected_ids)
destination.mkdir(exist_ok=True)
records = []
for case_id, item in sorted(actuals.items()):
    narrow = "narrow" in case_id or "sheet" in case_id
    is_window = "sheet" in case_id
    content = item.read_bytes()
    width, height = struct.unpack(">II", content[16:24])
    assert content[:8] == b"\x89PNG\r\n\x1a\n" and len(content) > 1024
    assert (width, height) == ((640, 1280) if narrow else (720, 1600)), (case_id, width, height)
    semantics = outputs / f"{case_id}-semantics.xml"
    window = outputs / f"{case_id}-window.txt"
    assert semantics.is_file(), case_id
    semantics_root = ET.parse(semantics).getroot()
    assert semantics_root.get("id") == case_id
    assert float(semantics_root.get("fontScale")) == (2 if narrow else 1)
    assert window.is_file() == is_window, f"Incorrect capture target for {case_id}"
    if is_window:
        window_text = window.read_text(encoding="utf-8")
        assert "IsDialog" in window_text and "screenshot_stage" not in window_text, case_id
        shutil.copy2(window, destination / window.name)
    shutil.copy2(item, destination / item.name)
    shutil.copy2(semantics, destination / semantics.name)
    records.append(dict(
        id=case_id, file=item.name, sha256=hashlib.sha256(content).hexdigest(),
        size=[width, height], fontScale=2 if narrow else 1,
        stageDp=[320, 640] if narrow else [360, 800],
        target="independent-dialog-root" if is_window else "screenshot_stage",
        window=window.read_text(encoding="utf-8").split("Printing")[0] if is_window else None,
    ))
for item in results.glob("TEST-*.xml"):
    shutil.copy2(item, destination / item.name)
shutil.copy2(args.log, destination / args.log.name)
source_hashes = {
    name: hashlib.sha256((root / name).read_bytes()).hexdigest()
    for name in (
        "app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsScreen.kt",
        "app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsComponents.kt",
    )
}
(destination / "manifest.json").write_text(json.dumps(dict(
    phase=args.phase, commandExitCode=args.exit_code, expectedMissingBaselines=16,
    acceptedPngHashesUnchanged=110, independentWindows=6,
    sourceSha256=source_hashes, cases=records,
), ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Frozen {len(records)} {args.phase} actuals, including 6 independent windows; 110 accepted PNGs unchanged.")
