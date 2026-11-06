"""冻结 P06 verify 的真实缺图 actual、窗口证据和报告；不写已接纳 PNG。"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("before", "after", "after-repeat"))
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, help="产生实际图片的工作树；默认当前任务所在主工作树")
    parser.add_argument("--output-directory", type=Path, help="独立冻结目录；已有manifest不可覆盖")
    parser.add_argument("--original-git-sources", action="store_true", help="补before使用原始Git blob字节，旧混合换行哈希保留在历史清单")
    args = parser.parse_args()
    research = Path(__file__).resolve().parent
    main_root = next(p for p in research.parents if (p / "app/build.gradle").is_file())
    root = args.source_root.resolve() if args.source_root else main_root
    assert (root / "app/build.gradle").is_file(), "来源工作树无app构建文件"
    run = args.run.resolve()
    baseline = read_json(research / "implementation-baseline-20261106.json")
    accepted = dict(line.split("  ", 1)[::-1] for line in (research / "accepted-png-hashes-at-start.txt").read_text(encoding="utf-8-sig").splitlines())
    actual_accepted = {p.relative_to(root).as_posix(): sha(p) for p in (root / "app/src/test/snapshots").rglob("*.png")}
    assert actual_accepted == accepted and len(accepted) == 158, "已接纳 PNG 漂移"
    extensions_path = research / "source-extensions.json"
    extensions = read_json(extensions_path)["sources"] if extensions_path.exists() else {}
    source_names = baseline["source_sha256"].keys() | extensions.keys()
    source_hashes = {name: sha(root / name) for name in sorted(source_names)}
    if args.phase == "before":
        expected_before = baseline["source_sha256"] | {name: row["originalSha256"] for name, row in extensions.items()}
        source_head = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
        assert source_head == baseline["head"], "补before必须基于指定原始提交"
        if args.original_git_sources:
            assert args.source_root and root != main_root, "原始Git字节补拍必须使用隔离工作树"
            expected_before = {name: hashlib.sha256(subprocess.check_output(["git", "-C", str(root), "show", f"{source_head}:{name}"])).hexdigest() for name in expected_before}
        assert source_hashes == expected_before, "before来源字节不符合声明的原始策略"
    elif args.original_git_sources:
        raise AssertionError("原始Git来源选项仅用于before")

    suites = [ET.parse(path).getroot() for path in (run / "test-results").glob("TEST-*.xml")]
    totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ("tests", "failures", "errors", "skipped")}
    assert totals == dict(tests=64, failures=64, errors=0, skipped=0), totals
    for suite in suites:
        for test in suite.findall("testcase"):
            failure = test.find("failure")
            assert failure is not None
            message = failure.get("message", "")
            assert "original file" in message or "缺少已登记" in message, message
    exit_code = int((run / "exit-code.txt").read_text(encoding="utf-8-sig").strip())
    assert exit_code == 1
    logs = (run / "run.log").read_text(encoding="utf-8-sig")
    task_lines = [line for line in logs.splitlines() if line.startswith("> Task")]
    assert "> Task :app:testDebugUnitTest FAILED" in task_lines, "必须实际执行截图测试"
    planned = read_json(research / "screenshot-matrix.json")["cases"]
    starts = {s.get("name").split(".")[-1]: datetime.fromisoformat(s.get("timestamp").replace("Z", "+00:00")).timestamp() for s in suites}
    output = root / "app/build/outputs/roborazzi"
    destination = args.output_directory.resolve() if args.output_directory else research / args.phase
    assert not (destination / "manifest.json").exists(), "冻结清单已存在，请保留来源，不原地覆盖"
    destination.mkdir(exist_ok=True)
    records = []
    environments = []
    for case in planned:
        name = case["id"]
        started = starts[case["testClass"]]
        paths = {kind: output / f"{name}{suffix}" for kind, suffix in {
            "image": "_actual.png", "semantics": "-semantics.xml", "measurements": "-measurements.txt", "window": "-window.txt",
        }.items()}
        for kind in ("image", "semantics", "measurements"):
            assert paths[kind].is_file() and paths[kind].stat().st_mtime >= started - 2, (name, kind, "陈旧/缺失证据")
            shutil.copy2(paths[kind], destination / paths[kind].name)
        content = paths["image"].read_bytes()
        assert content[:8] == b"\x89PNG\r\n\x1a\n"
        size = list(struct.unpack(">II", content[16:24]))
        semantic = ET.parse(paths["semantics"]).getroot()
        target = {"stage": "screenshot_stage", "dialog": "independent-dialog-root", "popup": "independent-popup-root"}[case["target"]]
        assert semantic.get("target") == target and semantic.get("id") == name
        assert float(semantic.get("fontScale")) == case["fontScale"]
        raw_measurements = paths["measurements"].read_text(encoding="utf-8")
        measurements = dict(line.split("=", 1) for line in raw_measurements.splitlines())
        assert float(measurements["density"]) == case["density"]
        assert float(measurements["fontScale"]) == case["fontScale"]
        environments.append({
            "fixedEpochMillis": int(measurements["clockStartMillis"]),
            "clock": "Robolectric ShadowPausedSystemClock; virtual time advances with deterministic UI actions; restored after each test",
            "locale": measurements["locale"], "timeZone": measurements["timeZone"],
            "density": float(measurements["density"]), "sdk": 35, "graphicsMode": "NATIVE", "application": "android.app.Application",
        })
        window = None
        if case["target"] != "stage":
            assert paths["window"].is_file() and paths["window"].stat().st_mtime >= started - 2
            raw_window = paths["window"].read_text(encoding="utf-8")
            expected_semantics = "IsPopup" if case["target"] == "popup" else "IsDialog"
            assert expected_semantics in raw_window and "screenshot_stage" not in raw_window, name
            window = raw_window.split("Printing")[0]
            shutil.copy2(paths["window"], destination / paths["window"].name)
            if case["target"] == "popup":
                assert measurements["popupViewOwner"].endswith("PopupLayout")
                assert measurements["popupRenderer"] == "native Canvas drawing the independent Popup AndroidComposeView"
        else:
            assert size == [case["widthDp"] * case["density"], case["heightDp"] * case["density"]]
        records.append({"id": name, "file": paths["image"].name, "sha256": sha(paths["image"]), "size": size,
                        "fontScale": case["fontScale"], "density": case["density"], "stageDp": [case["widthDp"], case["heightDp"]],
                        "state": case["state"], "theme": case["theme"], "dark": case["dark"], "target": target,
                        "window": window, "measurements": raw_measurements})
    assert len(records) == 63
    assert sum(c["target"] != "screenshot_stage" for c in records) == 18
    assert sum(c["target"] == "independent-popup-root" for c in records) == 4
    assert all(env == environments[0] for env in environments)
    tests = ["P06RenderTest.kt", "WorldBookRenderTest.kt", "ContextTransferRenderTest.kt", "LegacyTextFieldScrollProbe.kt", "WorldBookScreenshotE06Test.kt",
             "ContextTransferScreenshotE06Test.kt", "ScreenshotCases.kt", "ScreenshotStageTest.kt", "ScreenshotFixture.kt"]
    test_hashes = {f"app/src/test/java/com/example/myapplication/ui/screenshot/{name}": sha(root / "app/src/test/java/com/example/myapplication/ui/screenshot" / name) for name in tests}
    manifest = {"phase": args.phase, "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
                "command": ".\\gradlew.bat :app:verifyRoborazziDebug --tests com.example.myapplication.ui.screenshot.WorldBookScreenshotE06Test --tests com.example.myapplication.ui.screenshot.ContextTransferScreenshotE06Test --tests com.example.myapplication.ui.screenshot.ScreenshotBaselineGuardTest --console=plain",
                "commandExitCode": exit_code, "sourceCommit": baseline["head"], "sourceRoot": str(root), "expectedMissingBaselines": 63,
                "acceptedPngHashesUnchanged": 158, "independentWindows": 18, "popupCases": 4,
                "captureEnvironment": environments[0], "sourceSha256": source_hashes, "testSourceSha256": test_hashes, "cases": records}
    manifest["sourceBytePolicy"] = "exact-original-git-blob" if args.original_git_sources else "working-tree-bytes"
    for item in run.iterdir():
        if item.is_file():
            shutil.copy2(item, destination / item.name)
    shutil.copytree(run / "test-results", destination / "test-results", dirs_exist_ok=True)
    (destination / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (destination / "summary.json").write_text(json.dumps({"commandExitCode": exit_code, **totals, "actualTaskLines": task_lines,
                                                        "failureKind": "63 new missing-original failures + unchanged count guard; no behavior/window/isolation failures"}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Frozen {args.phase}: 63 fresh actuals, 18 independent windows / 4 popups; 158 accepted PNGs unchanged.")


if __name__ == "__main__":
    main()
