"""独立复核 P11 冻结来源、JUnit 与质量报告；不运行 Gradle、不写基准。"""

from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import xml.etree.ElementTree as ET


RESEARCH = Path(__file__).resolve().parent
ROOT = next(parent for parent in RESEARCH.parents if (parent / "app/build.gradle").is_file())


def read(name):
    return json.loads((RESEARCH / name).read_text(encoding="utf-8-sig"))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def sha(path):
    return digest(path.read_bytes())


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def junit(folder):
    suites = [ET.parse(path).getroot() for path in (RESEARCH / folder).glob("TEST-*.xml")]
    totals = {key: sum(int(suite.get(key, 0)) for suite in suites)
              for key in ("tests", "failures", "errors", "skipped")}
    return suites, {"suites": len(suites), **totals}


def lint_issues(path):
    issues = ET.parse(path).getroot().findall("issue")
    return Counter((issue.get("id"), issue.get("severity"), issue.get("message"),
                    tuple(node.get("file", "").replace("\\", "/")
                          for node in issue.findall("location"))) for issue in issues)


def main():
    assert git("rev-parse", "HEAD").decode().strip() == "ac22bb271007c814b6ef49b8499858acc815661c"
    assert not git("diff", "--cached", "--name-only").strip()
    start = read("workspace-start-20261105.json")
    before, after = read("before/manifest.json"), read("after/manifest.json")
    quality = read("quality-verified/summary.json")
    final = read("implementation-final-hashes.json")
    supplement = read("shared-dialog-baseline-20261105.json")
    assert after["sourceSha256"] == quality["sourceSha256"] == final["sourceSha256"]
    for name, expected in after["sourceSha256"].items():
        assert sha(ROOT / name) == expected, name

    base_sources = {}
    for name, expected in before["sourceSha256"].items():
        assert start["source_sha256"][name] == expected, name
        assert not any(entry.replace("\\", "/").endswith(" " + name)
                       for entry in start["initial_status_entries"]), name
        raw = git("show", "ac22bb27:" + name)
        lf = raw.replace(b"\r\n", b"\n")
        variants = {"git_bytes": digest(raw), "crlf": digest(lf.replace(b"\n", b"\r\n")), "lf": digest(lf)}
        base_sources[name] = {
            "frozen_raw_sha256": expected,
            "start_worktree_hash_matches": True,
            "clean_in_recorded_initial_git_status": True,
            "git_blob": git("rev-parse", "ac22bb27:" + name).decode().strip(),
            "direct_raw_matches": [kind for kind, value in variants.items() if value == expected],
        }
    settings = "app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsComponents.kt"
    settings_bytes = (ROOT / settings).read_bytes()
    assert settings_bytes.replace(b"\r\n", b"\n") == git("show", "ac22bb27:" + settings).replace(b"\r\n", b"\n")
    assert git("rev-parse", "ac22bb27:" + supplement["file"]).decode().strip() == supplement["git_blob"]
    raw = git("show", "ac22bb27:" + supplement["file"])
    lf = raw.replace(b"\r\n", b"\n")
    assert supplement["sha256"] in (digest(raw), digest(lf), digest(lf.replace(b"\n", b"\r\n")))

    inventory = RESEARCH / "accepted-png-hashes-before-p11.txt"
    assert sha(inventory) == "8a63c66d92c94dcd9f19fbcf38980a5bd5f9df436b6b9eb9198dbc1850b7a7fc"
    frozen = {line.split("  ", 1)[1]: line.split("  ", 1)[0] for line in inventory.read_text().splitlines()}
    current = {path.relative_to(ROOT).as_posix(): sha(path)
               for path in (ROOT / "app/src/test/snapshots").rglob("*.png")}
    assert len(current) == 126 and current == frozen
    for name in ("gradle.properties", "app/build.gradle", "docs/updates/dev.json", settings):
        assert sha(ROOT / name) == start["source_sha256"][name], name
    assert git("diff", "--", "app/src/test/snapshots/manifest.md") == (RESEARCH / "manifest-preexisting-ci.diff").read_bytes()

    phases = {}
    for phase, manifest in (("before", before), ("after", after)):
        records = {row["id"]: row for row in manifest["cases"]}
        assert len(manifest["cases"]) == len(records) == 32
        assert sum(row["target"] == "independent-dialog-root" for row in records.values()) == 12
        for name, row in records.items():
            content = (RESEARCH / phase / row["file"]).read_bytes()
            assert digest(content) == row["sha256"], (phase, name)
            assert content[:8] == b"\x89PNG\r\n\x1a\n"
            assert list(struct.unpack(">II", content[16:24])) == row["size"]
            semantics = ET.parse(RESEARCH / phase / (name + "-semantics.xml")).getroot()
            assert semantics.get("id") == name and float(semantics.get("fontScale")) == row["fontScale"]
            if row["target"] == "independent-dialog-root":
                window = (RESEARCH / phase / (name + "-window.txt")).read_text(encoding="utf-8")
                assert "IsDialog" in window and "screenshot_stage" not in window
            if "-narrow-" in name:
                value, maximum = re.search(r"pageScroll=([\d.]+)/([\d.]+)", row["measurements"]).groups()
                assert float(value) == float(maximum), (phase, name)
                assert "Unclipped" in row["measurements"]
        phases[phase] = {"pngs": 32, "windows": 12, "manifest_sha256": sha(RESEARCH / phase / "manifest.json")}
    for first in before["cases"]:
        second = next(row for row in after["cases"] if row["id"] == first["id"])
        assert all(first[key] == second[key] for key in ("fontScale", "stageDp", "target"))
    review = json.loads((RESEARCH.parent / "review/review-data.json").read_text(encoding="utf-8"))
    assert review["accepted"] is False and len(review["cases"]) == 32
    for row in review["cases"]:
        for phase in ("before", "after"):
            assert sha(RESEARCH.parent / "review" / row[phase]) == row[phase + "_sha256"]

    provider_suites, provider = junit("provider-final-results")
    assert provider == dict(suites=9, tests=52, failures=0, errors=0, skipped=0), provider
    assert sum(int(suite.get("tests")) for suite in provider_suites
               if suite.get("name").endswith("UiTokenSourceGuardTest")) == 14
    screenshot_suites, screenshots = junit("screenshot-final-results")
    assert screenshots == dict(suites=15, tests=159, failures=33, errors=0, skipped=0), screenshots
    for suite in screenshot_suites:
        for case in suite.findall("testcase"):
            failure = case.find("failure")
            if failure is not None:
                name = suite.get("name")
                assert (name.endswith("ProviderSettingsScreenshotE11Test") and "original file" in failure.get("message")) or (
                    name.endswith("ScreenshotBaselineGuardTest") and "缺少已登记" in failure.get("message")), (name, case.get("name"))
    latest = read("targeted-final-status.json")
    assert latest["uniqueTests"] == len(latest["results"]) == 144 and latest["latestFailures"] == 0
    test_ids = set()
    for entry in latest["results"]:
        key = entry["suite"], entry["name"]
        assert key not in test_ids
        test_ids.add(key)
        report = ET.parse(RESEARCH / entry["source"]).getroot()
        case = next(item for item in report.findall("testcase") if item.get("name") == entry["name"])
        assert all(case.find(status) is None for status in ("failure", "error", "skipped")), key
        assert entry["passed"] is True

    old_lint = ROOT / ".trellis/tasks/archive/2026-11/10-21-ui-token-settings-home/research/quality-final/lint-results-debug.xml"
    new_lint = RESEARCH / "quality-verified/lint-results-debug.xml"
    assert lint_issues(old_lint) == lint_issues(new_lint)
    severities = Counter(issue.get("severity") for issue in ET.parse(new_lint).getroot().findall("issue"))
    assert severities == Counter(Warning=55, Hint=3)
    apk = quality["apk"]
    assert sha(ROOT / apk["file"]) == apk["sha256"] and (ROOT / apk["file"]).stat().st_size == apk["size"]
    assert apk["versionName"] == "2.2.12-dev" and apk["versionCode"] == 20212 and apk["published"] is False
    assert quality["commandExitCode"] == 0 and quality["deviceTestsExecuted"] is False
    for required in (":app:compileDebugAndroidTestKotlin", ":app:packageDebug", ":app:lintAnalyzeDebug",
                     ":app:lintAnalyzeDebugAndroidTest", ":app:lintAnalyzeDebugUnitTest"):
        assert "> Task " + required in quality["taskLines"], required

    record = {
        "checked_at_utc": datetime.now(timezone.utc).isoformat(), "reviewer": "p11_check",
        "independent_audit": "passed", "before_source_proof": base_sources,
        "source_sha256": after["sourceSha256"], "accepted_pngs_unchanged": 126,
        "phases": phases, "provider_junit": provider, "screenshot_junit": screenshots,
        "targeted_distinct_latest_passed": 144, "lint": dict(severities), "lint_added_removed_issues": 0,
        "apk_sha256": apk["sha256"], "scope_protections_unchanged": True,
        "new_gradle_runs_in_this_audit": 0, "device_tests_executed": False, "human_visual_acceptance": False,
        "unchanged_settings_components_eol": {"crlf": settings_bytes.count(b"\r\n"), "lf_total": settings_bytes.count(b"\n")},
        "source_note": "Frozen worktree raw SHA equals the recorded clean start; six before files match Git LF bytes directly. ProviderSettingsScreen and SettingsComponents raw hashes are worktree-byte hashes, not claimed to equal Git blob bytes. Unchanged SettingsComponents normalizes exactly to ac22bb27 and contains mixed line endings.",
    }
    (RESEARCH / "check-evidence-audit-20261105.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in record.items() if key not in ("before_source_proof", "source_sha256")}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
