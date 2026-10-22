"""冻结 verify 的真实 actual，绝不写 src/test/snapshots。"""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

repo = next(parent for parent in Path(__file__).resolve().parents if (parent / ".trellis").is_dir())
research = Path(__file__).resolve().parent
phase = sys.argv[1]
assert phase in ("before", "after")
target = research / phase
if phase == "before":
    assert not target.exists(), "迁移前冻结不可覆盖"
target.mkdir(exist_ok=True)
frozen = json.loads((research / "baseline-sha256-before.json").read_text(encoding="utf-8-sig"))
for rel, digest in frozen.items():
    assert hashlib.sha256((repo / "app/src/test/snapshots" / rel).read_bytes()).hexdigest() == digest, rel
xml = repo / "app/build/test-results/testDebugUnitTest/TEST-com.example.myapplication.ui.screenshot.OfflineRoleplayScreenshotE04Test.xml"
tests = ET.parse(xml).getroot().findall("testcase")
assert len(tests) == 14
assert all("original file" in t.find("failure").attrib["message"] and "was not found" in t.find("failure").attrib["message"] for t in tests)
actuals = list((repo / "app/build/outputs/roborazzi").glob("E04_*_actual.png"))
assert len(actuals) == 14, len(actuals)
manifest = {}
for source in sorted(actuals):
    case = source.name.removesuffix("_actual.png")
    dest = target / f"{case}.png"
    shutil.copy2(source, dest)
    narrow = "narrow" in case
    manifest[case] = {
        "file": dest.name, "sha256": hashlib.sha256(dest.read_bytes()).hexdigest(),
        "width_dp": 320 if narrow else 360, "height_dp": 640 if narrow else 800,
        "density": 2, "font_scale": 2 if narrow else 1, "sdk": 35,
        "host": "Windows / Robolectric NATIVE", "locale": "zh-CN", "time_zone": "Asia/Shanghai",
        "capture": "verify missing-baseline actual; not accepted baseline",
    }
(target / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
shutil.copy2(xml, target / "verify-results.xml")
print(f"{phase}: {len(manifest)} actual PNG frozen; {len(frozen)} accepted PNG unchanged")
