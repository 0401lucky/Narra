"""Freeze verify-only actuals outside the accepted snapshot tree."""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[4]
phase = sys.argv[1]
destination = Path(__file__).parent / phase
destination.mkdir(exist_ok=True)
outputs = root / "app/build/outputs/roborazzi"
results = root / "app/build/test-results/testDebugUnitTest"
suite_file = results / "TEST-com.example.myapplication.ui.screenshot.SessionSidebarScreenshotE05Test.xml"
suite = ET.parse(suite_file).getroot()
assert len(suite.findall("testcase")) == 18
for test in suite.findall("testcase"):
    failure = test.find("failure")
    assert failure is not None and "original file" in failure.get("message", ""), ET.tostring(test).decode()

for item in results.glob("TEST-*.xml"):
    shutil.copy2(item, destination / item.name)
records = []
for item in sorted(outputs.glob("E05*_actual.png")):
    shutil.copy2(item, destination / item.name)
    content = item.read_bytes()
    width, height = struct.unpack(">II", content[16:24])
    case_id = item.name.removesuffix("_actual.png")
    window = outputs / f"{case_id}-window.txt"
    semantics = outputs / f"{case_id}-semantics.xml"
    assert semantics.is_file(), case_id
    shutil.copy2(semantics, destination / semantics.name)
    if window.exists():
        shutil.copy2(window, destination / window.name)
    records.append(dict(id=case_id, file=item.name, sha256=hashlib.sha256(content).hexdigest(),
        size=[width, height], fontScale=2 if any(token in case_id for token in ("narrow", "sheet", "dialog")) else 1,
        stageDp=[320, 640] if any(token in case_id for token in ("narrow", "sheet", "dialog")) else [360, 800],
        target="independent-dialog-root" if window.exists() else "screenshot_stage",
        window=window.read_text(encoding="utf-8").split("Printing")[0] if window.exists() else None))
assert len(records) == 18
frozen = Path(__file__).parent / "accepted-png-hashes-before-p05.txt"
for line in frozen.read_text(encoding="utf-8-sig").splitlines():
    expected, name = line.split("  ", 1)
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == expected, name
(destination / "manifest.json").write_text(json.dumps(dict(phase=phase, commandExitCode=1,
    expectedMissingBaselines=18, acceptedPngHashesUnchanged=92, cases=records), ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Frozen {len(records)} {phase} actuals; 92 accepted hashes unchanged")
