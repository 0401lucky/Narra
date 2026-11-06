"""汇总修正捕获的可审计来源链；旧图与旧manifest只读，新阶段必须先严格冻结。"""

import hashlib
import json
from pathlib import Path


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    research = Path(__file__).resolve().parent
    root = next(path for path in research.parents if (path / "app/build.gradle").is_file())
    proof = read_json(research / "capture-runs/recovery-preparation/source-proof.json")
    old = read_json(research / "before/manifest.json")
    recovered = read_json(research / "before-corrected/manifest.json")
    changed_ids = ["E06_transfer-preview-dynamic-light", "E06_transfer-preview-dynamic-dark", "E06_transfer-preview-notice-narrow-light"]
    old_hashes = {case["id"]: case["sha256"] for case in old["cases"]}
    untouched = [case for case in recovered["cases"] if case["id"] not in changed_ids]
    differences = [case["id"] for case in untouched if old_hashes[case["id"]] != case["sha256"]]
    assert len(untouched) == 60 and not differences
    assert recovered["testSourceSha256"] == proof["overlaySources"] and len(proof["overlaySources"]) == 9
    assert recovered["sourceSha256"] == {name: item["gitBlobSha256"] for name, item in proof["originalSources"].items()}
    current_products = {name: sha(root / name) for name in proof["mainProductSourcesAtStart"]}
    current_pngs = {path.relative_to(root).as_posix(): sha(path) for path in (root / "app/src/test/snapshots").rglob("*.png")}
    assert current_products == proof["mainProductSourcesAtStart"]
    assert current_pngs == proof["acceptedPngHashesAtStart"] and len(current_pngs) == 158
    assert {name: sha(root / name) for name in proof["overlaySources"]} == proof["overlaySources"]
    phases = {}
    for name in ("before-corrected", "after-corrected", "after-repeat-corrected"):
        manifest_path = research / name / "manifest.json"
        if manifest_path.exists():
            manifest = read_json(manifest_path)
            assert len(manifest["cases"]) == 63 and manifest["testSourceSha256"] == proof["overlaySources"]
            if name != "before-corrected":
                assert manifest["sourceSha256"] == current_products
            phases[name] = {"directory": f"research/{name}", "manifestSha256": sha(manifest_path)}
    repeat_result = None
    unchanged_after = None
    if "after-repeat-corrected" in phases:
        after = read_json(research / "after-corrected/manifest.json")
        repeat = read_json(research / "after-repeat-corrected/manifest.json")
        after_hashes = {case["id"]: case["sha256"] for case in after["cases"]}
        different = [case["id"] for case in repeat["cases"] if after_hashes[case["id"]] != case["sha256"]]
        assert not different
        repeat_result = {"expected": 63, "matched": 63, "differences": different}
        legacy_after = read_json(research / "after/manifest.json")
        legacy_after_hashes = {case["id"]: case["sha256"] for case in legacy_after["cases"]}
        after_differences = [case["id"] for case in after["cases"] if case["id"] not in changed_ids and legacy_after_hashes[case["id"]] != case["sha256"]]
        assert not after_differences
        unchanged_after = {"expected": 60, "matched": 60, "differences": after_differences}
    final_result = None
    final_report = research / "capture-runs/all-screenshots-corrected-01/summary.json"
    if final_report.exists():
        result = read_json(final_report)
        assert result["tests"] == 222 and result["expectedMissingBaselineFailures"] == 64 and result["acceptedScreenshotPasses"] == 158
        assert not result["unexpectedFailures"] and result["correctedActualsMatchingAfter"] == 63
        final_result = {"file": "research/capture-runs/all-screenshots-corrected-01/summary.json", "sha256": sha(final_report), **result}
    recovery = {
        "status": "captures-complete" if repeat_result else "after-capture-in-progress",
        "originalCommit": proof["originalCommit"],
        "originalBefore": {"directory": "research/before", "manifestSha256": sha(research / "before/manifest.json")},
        "recoveredBefore": phases["before-corrected"],
        "changedCaseIds": changed_ids,
        "overlaySources": proof["overlaySources"],
        "unchangedCases": {"expected": 60, "matched": 60, "differences": differences},
        "isolatedWorktree": proof["isolatedWorktree"],
        "sourceBytePolicy": proof["sourceBytePolicy"],
        "originalSources": proof["originalSources"],
        "sourcePreparationProof": {"file": "research/capture-runs/recovery-preparation/source-proof.json", "sha256": sha(research / "capture-runs/recovery-preparation/source-proof.json")},
        "historicalEolEvidence": {"file": "research/original-source-byte-mapping.json", "sha256": sha(research / "original-source-byte-mapping.json")},
        "caseParameters": read_json(research / "screenshot-matrix.json")["cases"],
        "phases": phases,
        "repeatStability": repeat_result,
        "unchangedAfterCases": unchanged_after,
        "finalScreenshotValidation": final_result,
        "reviewPolicy": "Use the corrected phases for the next review. Original before/after/after-repeat remain as historical evidence of the previous capture strategy; no PNG has been accepted.",
        "mainWorkspaceUnchanged": {
            "sourceHashesAtStart": proof["mainProductSourcesAtStart"], "sourceHashesAfterCapture": current_products,
            "changedProductSources": [], "acceptedPngCount": 158, "changedAcceptedPngs": [],
            "acceptedPngHashesAtStart": proof["acceptedPngHashesAtStart"], "acceptedPngHashesAfterCapture": current_pngs,
        },
        "preservedLegacyManifests": {name: sha(research / name / "manifest.json") for name in ("before", "after", "after-repeat")},
        "captureRules": {
            "dynamic": "align actual source/section row; complete notices, five actual count rows, conflict heading and all TextLayout lines of the first conflict",
            "narrowNotice": "align actual notice Surface; complete title and entire actual body",
            "otherCases": "60 capture paths unchanged; all 60 recovered before PNG hashes equal the originals",
        },
    }
    target = research / "capture-recovery.json"
    temporary = target.with_suffix(".tmp")
    temporary.write_text(json.dumps(recovery, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(target)
    print(f"Recovery source chain updated: {len(phases)} phases, 60 unchanged before PNGs, 14 original Git source files, 9 capture overlays; main products/158 PNGs unchanged.")


if __name__ == "__main__":
    main()
