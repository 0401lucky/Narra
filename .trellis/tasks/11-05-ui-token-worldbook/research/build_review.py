"""生成 P06 真实前后对照；验证来源和稳定帧，只写任务审阅目录。"""

import argparse
import hashlib
import html
import importlib.util
import json
import math
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


TASK = Path(__file__).resolve().parent.parent
ROOT = next(parent for parent in TASK.parents if (parent / ".trellis").is_dir())
SUPPORT = (
    ROOT
    / ".trellis/tasks/archive/2026-11/10-21-ui-token-session-sidebar/research/build_review.py"
)
spec = importlib.util.spec_from_file_location("p06_review_support", SUPPORT)
support = importlib.util.module_from_spec(spec)
spec.loader.exec_module(support)
TARGETS = {
    "stage": "screenshot_stage",
    "dialog": "independent-dialog-root",
    "popup": "independent-popup-root",
}
GROUPS = {"worldbook": "列表与详情", "editor": "条目编辑", "transfer": "数据导入导出"}
THEMES = {
    "matcha": "抹茶", "vanilla": "香草", "ocean": "海洋",
    "dynamic": "固定动态色", "fixed-dynamic": "固定动态色",
}
RECOVERED_PREVIEW_CASES = {
    "E06_transfer-preview-dynamic-light",
    "E06_transfer-preview-dynamic-dark",
    "E06_transfer-preview-notice-narrow-light",
}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def task_path(value):
    path = (TASK / value).resolve()
    if not path.is_relative_to(TASK):
        raise SystemExit(f"审阅输入输出须位于当前任务内：{value}")
    return path


def capture_recovery(before_folder):
    path = TASK / "research/capture-recovery.json"
    if not path.exists():
        return None
    record = read_json(path)
    recovered = record["recoveredBefore"]
    if task_path(recovered["directory"]) != before_folder.resolve():
        return None
    start = read_json(TASK / "research/implementation-baseline-20261106.json")
    original = record["originalBefore"]
    original_folder = task_path(original["directory"])
    if (
        record["originalCommit"] != start["head"]
        or support.sha256(original_folder / "manifest.json") != original["manifestSha256"]
        or support.sha256(before_folder / "manifest.json") != recovered["manifestSha256"]
        or set(record["changedCaseIds"]) != RECOVERED_PREVIEW_CASES
    ):
        raise SystemExit("补取 before 的原始提交、清单或三项预览范围不符")
    old_manifest = read_json(original_folder / "manifest.json")
    new_manifest = read_json(before_folder / "manifest.json")
    if (
        old_manifest["sourceSha256"] != start["source_sha256"]
        or old_manifest["captureEnvironment"] != new_manifest["captureEnvironment"]
        or new_manifest["sourceCommit"] != start["head"]
        or new_manifest["testSourceSha256"] != record["overlaySources"]
        or new_manifest["testSourceSha256"].keys() != old_manifest["testSourceSha256"].keys()
    ):
        raise SystemExit("补取 before 的环境、原始源码或捕获覆盖来源不符")
    old_rows = {row["id"]: row for row in old_manifest["cases"]}
    new_rows = {row["id"]: row for row in new_manifest["cases"]}
    if len(old_rows) != 63 or old_rows.keys() != new_rows.keys():
        raise SystemExit("补取 before 必须保留完整63项")
    unchanged = set(new_rows) - RECOVERED_PREVIEW_CASES
    for name, row in old_rows.items():
        original_image = (original_folder / row["file"]).resolve()
        if not original_image.is_relative_to(original_folder) or support.sha256(original_image) != row["sha256"]:
            raise SystemExit(f"原始 before 图片不得改写：{name}")
        for key in ("fontScale", "stageDp", "density", "target", "state", "theme", "dark"):
            if row[key] != new_rows[name][key]:
                raise SystemExit(f"补取 before 的场景参数漂移：{name}/{key}")
        if name in unchanged and row["sha256"] != new_rows[name]["sha256"]:
            raise SystemExit(f"未修取景的60项 before 出现变化：{name}")
    verification = record["unchangedCases"]
    if verification["expected"] != 60 or verification["matched"] != 60 or verification["differences"]:
        raise SystemExit("补取记录必须证明其余60张 before 一致")
    return record


def source_extensions(before_folder):
    path = TASK / "research/source-extensions.json"
    if not path.exists():
        return {}
    record = read_json(path)
    start = read_json(TASK / "research/implementation-baseline-20261106.json")
    recovery = capture_recovery(before_folder)
    original_manifest_hash = (
        recovery["originalBefore"]["manifestSha256"] if recovery
        else support.sha256(before_folder / "manifest.json")
    )
    if (
        record["sourceCommit"] != start["head"]
        or record["beforeManifestSha256"] != original_manifest_hash
    ):
        raise SystemExit("补充源码来源必须对应原始 before 提交与清单")
    for relative, entry in record["sources"].items():
        if relative in start["source_sha256"] or not (ROOT / relative).resolve().is_relative_to(ROOT):
            raise SystemExit(f"补充源码路径重复或越界：{relative}")
        original = task_path(entry["originalFile"])
        if support.sha256(original) != entry["originalSha256"]:
            raise SystemExit(f"补充原始源码哈希不符：{relative}")
        committed = subprocess.check_output(
            ["git", "show", f"{start['head']}:{relative}"], cwd=ROOT
        )
        if original.read_bytes() != committed:
            raise SystemExit(f"补充原始源码与 before 提交不一致：{relative}")
    return record["sources"]


def evidence(folder, planned):
    manifest = read_json(folder / "manifest.json")
    rows = manifest["cases"]
    records = {row["id"]: row for row in rows}
    if len(rows) != 63 or records.keys() != planned.keys():
        raise SystemExit(f"须完整包含63个唯一规划ID：{folder}")
    images = {}
    for name, row in records.items():
        case = planned[name]
        path = (folder / row["file"]).resolve()
        if not path.is_relative_to(folder) or not path.is_file():
            raise SystemExit(f"缺少本阶段图片：{name}")
        if support.sha256(path) != row["sha256"]:
            raise SystemExit(f"图片哈希不匹配：{folder.name}/{name}")
        with Image.open(path) as picture:
            size = list(picture.size)
        if size != row["size"]:
            raise SystemExit(f"图片尺寸记录不符：{name}")
        expected_dp = [case["widthDp"], case["heightDp"]]
        if (
            row["stageDp"] != expected_dp
            or row["fontScale"] != case["fontScale"]
            or row["density"] != case["density"]
            or row["target"] != TARGETS[case["target"]]
        ):
            raise SystemExit(f"捕获参数与规划不符：{name}")
        semantic = ET.parse(folder / f"{name}-semantics.xml").getroot()
        if (
            semantic.get("id") != name
            or semantic.get("target") != row["target"]
            or float(semantic.get("fontScale", "nan")) != row["fontScale"]
        ):
            raise SystemExit(f"原始语义参数与清单不符：{name}")
        measurements = (folder / f"{name}-measurements.txt").read_text(encoding="utf-8")
        if measurements != row.get("measurements"):
            raise SystemExit(f"原始测量与清单不符：{name}")
        expected_pixels = [round(value * case["density"]) for value in expected_dp]
        if case["target"] == "stage":
            if size != expected_pixels:
                raise SystemExit(f"页面捕获尺寸不符：{name}")
        else:
            if not row.get("window"):
                raise SystemExit(f"缺少真实独立窗口证据：{name}")
            if any(value <= 0 or value > limit for value, limit in zip(size, expected_pixels)):
                raise SystemExit(f"窗口超出规划宿主：{name}")
            # 清单仅保存窗口头；独立 root 的标记在原始 sidecar 的语义树中。
            window = (folder / f"{name}-window.txt").read_text(encoding="utf-8")
            marker = "IsPopup" if case["target"] == "popup" else "IsDialog"
            if (
                window.split("Printing", 1)[0] != row["window"]
                or not re.search(rf"^\s*\[{marker}\]\s*$", window, re.MULTILINE)
                or "screenshot_stage" in window
                or window.split("Printing", 1)[-1] != (semantic.text or "").split("Printing", 1)[-1]
            ):
                raise SystemExit(f"独立窗口证明不符：{name}")
            if case["target"] == "popup" and (
                "popupViewOwner=androidx.compose.ui.window.PopupLayout" not in measurements
                or "popupRenderer=native Canvas drawing the independent Popup AndroidComposeView" not in measurements
            ):
                raise SystemExit(f"缺少独立 Popup 像素来源：{name}")
        images[name] = path
    if not manifest.get("captureEnvironment"):
        raise SystemExit(f"缺少固定时钟/locale/时区记录：{folder}")
    return manifest, records, images


def check_preview_evidence(rows):
    def rect(value):
        match = re.fullmatch(r"Rect\.fromLTRB\(([^,]+), ([^,]+), ([^,]+), ([^)]+)\)", value)
        if not match:
            raise SystemExit(f"预览边界格式不符：{value}")
        bounds = tuple(float(part) for part in match.groups())
        if not all(math.isfinite(part) for part in bounds) or bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
            raise SystemExit(f"预览边界无有效面积：{value}")
        return bounds

    def inside(bounds, viewport):
        return (
            bounds[0] >= viewport[0] - 0.5 and bounds[1] >= viewport[1] - 0.5
            and bounds[2] <= viewport[2] + 0.5 and bounds[3] <= viewport[3] + 0.5
        )

    for name in RECOVERED_PREVIEW_CASES:
        measured = dict(line.split("=", 1) for line in rows[name]["measurements"].splitlines())
        narrow = name == "E06_transfer-preview-notice-narrow-light"
        anchor = "notice-card" if narrow else "source-and-section-row"
        if measured.get("previewAnchorKind") != anchor or "previewViewport" not in measured:
            raise SystemExit(f"预览须使用有完整正文边界的修正捕获：{name}")
        viewport = rect(measured["previewViewport"])
        texts = ["previewNoticeTitle", "previewNoticeBody"]
        boxes = list(texts)
        if not narrow:
            texts += ["previewSource", "previewSection", "previewConflictTitle"]
            for index in range(5):
                boxes.append(f"previewCountRow{index}")
                texts += [f"previewCountTitle{index}", f"previewCountValue{index}"]
            boxes += texts[2:]
        for key in boxes:
            if measured.get(f"{key}FullyVisible") != "true":
                raise SystemExit(f"预览内容没有完整显示：{name}/{key}")
            for suffix in ("", "Unclipped"):
                if not inside(rect(measured[f"{key}{suffix}"]), viewport):
                    raise SystemExit(f"预览实际边界超出视口：{name}/{key}{suffix}")
        for key in texts:
            if not measured.get(f"{key}Text") or int(measured.get(f"{key}TextLines", 0)) < 1:
                raise SystemExit(f"预览缺少实际文字布局：{name}/{key}")
        if not narrow:
            if measured.get("previewFirstConflictFullyVisible") != "true" or not measured.get("previewFirstConflictText"):
                raise SystemExit(f"首条冲突缺少完整文本证据：{name}")
            lines = measured.get("previewFirstConflictLines", "").split(" / ")
            if not lines or not all(inside(rect(line), viewport) for line in lines):
                raise SystemExit(f"首条冲突的实际文本行超出视口：{name}")


def baselines(planned):
    frozen = {}
    for line in (TASK / "research/accepted-png-hashes-at-start.txt").read_text(
        encoding="utf-8-sig"
    ).splitlines():
        digest, relative = line.split(None, 1)
        frozen[relative] = digest
    approval_path = TASK / "research/visual-acceptance.json"
    approval = read_json(approval_path) if approval_path.exists() else {}
    accepted = approval.get("accepted") is True
    expected = dict(frozen)
    if accepted:
        if approval["candidates"].keys() != planned.keys():
            raise SystemExit("人工接纳记录须包含全部63个精确哈希")
        expected.update(
            {
                f"app/src/test/snapshots/{planned[name]['testClass']}/{name}.png": digest
                for name, digest in approval["candidates"].items()
            }
        )
    current = {
        path.relative_to(ROOT).as_posix(): support.sha256(path)
        for path in (ROOT / "app/src/test/snapshots").rglob("*.png")
    }
    if len(frozen) != 158 or current != expected:
        raise SystemExit("基准与原158张冻结清单/人工接纳记录不一致")
    return approval, accepted


def check_before(folder, manifest, rows, planned):
    start = read_json(TASK / "research/implementation-baseline-20261106.json")
    if manifest["sourceSha256"] != start["source_sha256"]:
        raise SystemExit("before未完整匹配启动时13项源码/配置")
    for relative, digest in manifest["sourceSha256"].items():
        if support.sha256(ROOT / relative) != digest:
            raise SystemExit(f"before复核时产品已经改变：{relative}")
    if not manifest.get("testSourceSha256"):
        raise SystemExit("before缺少捕获测试源码")
    for relative, digest in manifest["testSourceSha256"].items():
        if support.sha256(ROOT / relative) != digest:
            raise SystemExit(f"before复核时捕获测试已经改变：{relative}")
    checked = {}
    for class_name, expected in (
        ("WorldBookScreenshotE06Test", 47),
        ("ContextTransferScreenshotE06Test", 16),
    ):
        report = folder / "test-results" / f"TEST-com.example.myapplication.ui.screenshot.{class_name}.xml"
        suite = ET.parse(report).getroot()
        tests = suite.findall("testcase")
        if len(tests) != expected or int(suite.get("errors", 0)) or int(suite.get("skipped", 0)):
            raise SystemExit(f"before测试数量/错误/跳过不符：{class_name}")
        observed_ids = {
            "E06_" + test.get("name").removeprefix("e06_").replace("_", "-")
            for test in tests
        }
        expected_ids = {name for name, case in planned.items() if case["testClass"] == class_name}
        if observed_ids != expected_ids:
            raise SystemExit(f"原始JUnit与规划场景ID不一致：{class_name}")
        for test in tests:
            failure = test.find("failure")
            if failure is None or "original file" not in failure.get("message", "").lower():
                raise SystemExit(f"before存在非缺图结果：{class_name}/{test.get('name')}")
        checked[class_name] = len(tests)
    guard = ET.parse(
        folder / "test-results" / "TEST-com.example.myapplication.ui.screenshot.ScreenshotBaselineGuardTest.xml"
    ).getroot()
    guard_tests = guard.findall("testcase")
    if (
        len(guard_tests) != 1 or guard_tests[0].find("failure") is None
        or int(guard.get("errors", 0)) or int(guard.get("skipped", 0))
    ):
        raise SystemExit("计数守卫应保留1项缺基准失败")
    guard_message = guard_tests[0].find("failure").get("message", "")
    missing_paths = set(
        re.findall(
            r"(?:WorldBookScreenshotE06Test|ContextTransferScreenshotE06Test)/E06_[A-Za-z0-9_-]+\.png",
            guard_message,
        )
    )
    expected_paths = {f"{case['testClass']}/{name}.png" for name, case in planned.items()}
    if "缺少已登记的基准 PNG：" not in guard_message or missing_paths != expected_paths:
        raise SystemExit("计数守卫失败并非仅缺少本批63张新图")
    return {
        "phase": "before",
        "complete_cases": len(rows),
        "independent_windows": sum(row["target"] != "screenshot_stage" for row in rows.values()),
        "popups": sum(row["target"] == "independent-popup-root" for row in rows.values()),
        "expected_missing_baselines": checked,
        "baseline_guard_failures": 1,
        "product_source_hashes_unchanged": 13,
        "old_baselines_unchanged": 158,
        "manifest_sha256": support.sha256(folder / "manifest.json"),
    }


def contact_sheet(rows, output, filename):
    cell_width, cell_height = 640, 810
    sheet = Image.new("RGB", (1280, ((len(rows) + 1) // 2) * cell_height), "#edf1ef")
    draw = ImageDraw.Draw(sheet)
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    font = ImageFont.truetype(str(font_path), 17) if font_path.exists() else ImageFont.load_default()
    for index, row in enumerate(rows):
        x, y = (index % 2) * cell_width, (index // 2) * cell_height
        draw.text((x + 12, y + 8), row["id"], fill="#243b30", font=font)
        for column, (key, label) in enumerate((("before", "修改前"), ("after", "本轮候选"))):
            draw.text((x + 12 + column * 320, y + 38), label, fill="#243b30", font=font)
            with Image.open(output / row[key]) as original:
                picture = original.convert("RGBA")
                picture.thumbnail((300, 730))
                # Popup原图的透明圆角需与展示底色合成，不能直接丢弃alpha变成黑角。
                sheet.paste(picture, (x + column * 320 + (320 - picture.width) // 2, y + 68), picture)
    sheet.save(output / filename)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", default="research/before")
    parser.add_argument("--after", default="research/after")
    parser.add_argument("--repeat", default="research/after-repeat")
    parser.add_argument("--output", default="review")
    parser.add_argument("--check-before", action="store_true", help="只读复核before及原始JUnit，尚不生成审阅页")
    args = parser.parse_args()
    matrix = read_json(TASK / "research/screenshot-matrix.json")
    planned = {case["id"]: case for case in matrix["cases"]}
    if len(planned) != len(matrix["cases"]) or len(planned) != 63:
        raise SystemExit("规划矩阵须含63个唯一场景")
    approval, accepted = baselines(planned)
    before_manifest, before_rows, before = evidence(task_path(args.before), planned)
    if args.check_before:
        if accepted:
            raise SystemExit("before复核用于产品修改和新图接纳之前")
        print(json.dumps(check_before(task_path(args.before), before_manifest, before_rows, planned), ensure_ascii=False))
        return
    after_manifest, after_rows, after = evidence(task_path(args.after), planned)
    repeat_manifest, repeat_rows, repeat = evidence(task_path(args.repeat), planned)
    for rows in (before_rows, after_rows, repeat_rows):
        check_preview_evidence(rows)
    for name in planned:
        for key in ("fontScale", "stageDp", "density", "target"):
            if not before_rows[name][key] == after_rows[name][key] == repeat_rows[name][key]:
                raise SystemExit(f"三轮捕获参数不一致：{name}/{key}")
        if after_rows[name]["sha256"] != repeat_rows[name]["sha256"]:
            raise SystemExit(f"重复after不稳定：{name}")
    if not (
        before_manifest["captureEnvironment"]
        == after_manifest["captureEnvironment"]
        == repeat_manifest["captureEnvironment"]
    ):
        raise SystemExit("前后及重复捕获的固定环境不同")
    if after_manifest["sourceSha256"] != repeat_manifest["sourceSha256"]:
        raise SystemExit("重复after时产品源码不同")
    if after_manifest["testSourceSha256"] != repeat_manifest["testSourceSha256"]:
        raise SystemExit("重复after时捕获测试源码不同")
    if not after_manifest["testSourceSha256"]:
        raise SystemExit("缺少捕获测试来源")
    for relative, digest in after_manifest["testSourceSha256"].items():
        if support.sha256(ROOT / relative) != digest:
            raise SystemExit(f"after与当前捕获测试源码不同：{relative}")
    if before_manifest["testSourceSha256"] != after_manifest["testSourceSha256"]:
        raise SystemExit("前后捕获策略源码不同；须先核对并保存同策略的真实before")
    start = read_json(TASK / "research/implementation-baseline-20261106.json")
    recovery = capture_recovery(task_path(args.before))
    extensions = source_extensions(task_path(args.before))
    expected_source_paths = start["source_sha256"].keys() | extensions.keys()
    if recovery:
        expected_before_sources = {
            relative: hashlib.sha256(subprocess.check_output(
                ["git", "show", f"{start['head']}:{relative}"], cwd=ROOT
            )).hexdigest()
            for relative in expected_source_paths
        }
        if after_manifest["sourceSha256"] != read_json(TASK / "research/after/manifest.json")["sourceSha256"]:
            raise SystemExit("取景修正期间不得改变已验证的产品源码")
    else:
        expected_before_sources = start["source_sha256"]
    if before_manifest["sourceSha256"] != expected_before_sources:
        raise SystemExit("before源码须匹配原始冻结清单或有证明的原始Git字节")
    if after_manifest["sourceSha256"].keys() != expected_source_paths:
        raise SystemExit("after源码/配置范围与实施起点不一致")
    for relative, digest in after_manifest["sourceSha256"].items():
        if support.sha256(ROOT / relative) != digest:
            raise SystemExit(f"after与当前产品源码不同：{relative}")
    candidates = {name: row["sha256"] for name, row in after_rows.items()}
    if accepted and candidates != approval["candidates"]:
        raise SystemExit("after与人工接纳的精确SHA不同")

    output = task_path(args.output)
    if output.parent != TASK or not output.name.startswith("review"):
        raise SystemExit("输出须为任务根目录下的review或review-*，不能覆盖冻结证据")
    for folder in ("before", "after", "diff"):
        (output / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for name, case in planned.items():
        paths = {folder: f"{folder}/{name}.png" for folder in ("before", "after", "diff")}
        shutil.copyfile(before[name], output / paths["before"])
        shutil.copyfile(after[name], output / paths["after"])
        overlay, changed, total, first_size, second_size = support.difference(before[name], after[name])
        overlay.save(output / paths["diff"])
        row = after_rows[name]
        label = (
            f"{case['label']} · {THEMES.get(case['theme'], case['theme'])}"
            f" · {'深色' if case['dark'] else '浅色'}"
        )
        rows.append(
            {
                "id": name,
                "label": label,
                "group": case["group"],
                "kind": case["target"],
                "font_scale": row["fontScale"],
                "stage_dp": row["stageDp"],
                "density": row["density"],
                "before_size_px": first_size,
                "after_size_px": second_size,
                "before_source": before[name].relative_to(ROOT).as_posix(),
                "after_source": after[name].relative_to(ROOT).as_posix(),
                "before_sha256": before_rows[name]["sha256"],
                "after_sha256": row["sha256"],
                "repeat_sha256": repeat_rows[name]["sha256"],
                "before_window": before_rows[name].get("window"),
                "after_window": row.get("window"),
                "before_measurements": before_rows[name].get("measurements"),
                "after_measurements": row.get("measurements"),
                "changed_pixels": changed,
                "changed_ratio": changed / total,
                **paths,
            }
        )
    data = {
        "expected_new_cases": 63,
        "complete_pairs": len(rows),
        "changed_pairs": sum(row["changed_pixels"] > 0 for row in rows),
        "stable_repeat_pairs": 63,
        "old_baselines_unchanged": 158,
        "independent_windows_per_phase": 18,
        "popups_per_phase": 4,
        "accepted": accepted,
        "before_manifest_sha256": support.sha256(task_path(args.before) / "manifest.json"),
        "after_manifest_sha256": support.sha256(task_path(args.after) / "manifest.json"),
        "repeat_manifest_sha256": support.sha256(task_path(args.repeat) / "manifest.json"),
        "capture_environment": after_manifest["captureEnvironment"],
        "capture_recovery": recovery,
        "before_source_sha256": before_manifest["sourceSha256"],
        "supplemental_original_sources": extensions,
        "after_source_sha256": after_manifest["sourceSha256"],
        "cases": rows,
    }
    (output / "review-data.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    cards = []
    for row in rows:
        name, label = html.escape(row["id"]), html.escape(row["label"])
        figures = "".join(
            f'<figure><figcaption>{caption}</figcaption><a href="{row[key]}" target="_blank">'
            f'<img src="{row[key]}" loading="lazy" alt="{label} {caption}"></a></figure>'
            for key, caption in (("before", "修改前"), ("after", "修改后"), ("diff", "变化位置（粉色）"))
        )
        details = html.escape(
            f"修改前：\n{row['before_window'] or ''}\n{row['before_measurements'] or ''}\n\n"
            f"修改后：\n{row['after_window'] or ''}\n{row['after_measurements'] or ''}\n\n"
            f"before SHA-256：{row['before_sha256']}\nafter/repeat SHA-256：{row['after_sha256']}"
        )
        cards.append(
            f'<article id="{name}" data-search="{name} {label}" data-group="{row["group"]}" '
            f'data-kind="{row["kind"]}"><h2>{label}</h2><code>{name}</code>'
            f'<p>{GROUPS[row["group"]]} · 字体 {row["font_scale"]:.0%} · '
            f'变化 {row["changed_pixels"]:,} 像素（{row["changed_ratio"]:.2%}）</p>'
            f'<div class="images">{figures}</div><details><summary>捕获证据与哈希</summary>'
            f'<pre>{details}</pre></details></article>'
        )
    status = "已由用户明确接纳" if accepted else "候选，等待人工接纳"
    page = """<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Narra v2.3 世界书与资料库前后对照</title><style>
*{box-sizing:border-box}body{margin:0;background:#edf1ef;color:#243b30;font:16px/1.6 system-ui,sans-serif}
header{padding:18px 24px;background:white;border-bottom:1px solid #ccd9d0}h1{font-size:22px;margin:0}
header p{margin:6px 0}input,select{font:inherit;padding:8px 12px;min-height:48px;border:1px solid #83998b;
border-radius:8px;max-width:100%}input{width:320px}.filters{display:flex;gap:12px;flex-wrap:wrap;align-items:end}
label{display:grid;gap:4px}main{max-width:1280px;margin:auto;padding:16px}
article{background:white;border:1px solid #ccd9d0;border-radius:16px;padding:16px;margin:16px 0}
h2{font-size:17px;overflow-wrap:anywhere;margin:0}code{font-size:13px;overflow-wrap:anywhere}
.images{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}details{margin-top:12px}
summary{cursor:pointer;min-height:48px;padding:10px 0}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px}
figure{margin:0}figcaption{margin-bottom:8px}img{width:100%;height:auto;border:1px solid #ccd9d0}
article[hidden]{display:none}@media(max-width:640px){main{padding:6px}article{padding:8px}.images{gap:5px}
figcaption{font-size:12px}header{padding:16px}}
</style><header><h1>Narra v2.3 世界书与资料库前后对照</h1>
<p>63组真实前后截图，__STATUS__。全部after经第二轮捕获逐张核对一致，原158张基准保持。</p>
<p>列表/详情27组、编辑20组、数据导入导出16组；含18个独立窗口，其中4个Popup。点击图片查看原尺寸。</p>
<p>窗口尺寸变化时差异图补透明画布对齐，原图不裁切或拉伸。</p>
<p><a href="../verification.md">验证记录</a> · <a href="review-data.json">完整参数与SHA-256</a></p>
<div class="filters"><label>筛选用例<input id="search" placeholder="列表、正文、导入…"></label>
<label>页面<select id="group"><option value="">全部页面</option><option value="worldbook">列表与详情</option>
<option value="editor">条目编辑</option><option value="transfer">数据导入导出</option></select></label>
<label>捕获类型<select id="kind"><option value="">全部类型</option><option value="stage">页面</option>
<option value="dialog">Dialog / Sheet</option><option value="popup">Popup</option></select></label>
<span id="count" role="status">显示63组</span></div></header><main>__CARDS__</main><script>
const search=document.getElementById('search'),group=document.getElementById('group'),kind=document.getElementById('kind');
function filter(){const term=search.value.toLowerCase();let visible=0;document.querySelectorAll('article').forEach(a=>{
a.hidden=!a.dataset.search.toLowerCase().includes(term)||(group.value&&a.dataset.group!==group.value)||
(kind.value&&a.dataset.kind!==kind.value);if(!a.hidden)visible++;});
document.getElementById('count').textContent='显示'+visible+'组';}
search.addEventListener('input',filter);group.addEventListener('change',filter);kind.addEventListener('change',filter);
</script></html>"""
    (output / "index.html").write_text(
        page.replace("__STATUS__", status).replace("__CARDS__", "\n".join(cards)), encoding="utf-8"
    )
    for group in GROUPS:
        selected = [row for row in rows if row["group"] == group]
        for start in range(0, len(selected), 6):
            contact_sheet(selected[start:start + 6], output, f"{group}-{start // 6 + 1}.png")
    print(
        json.dumps(
            {key: data[key] for key in (
                "complete_pairs", "stable_repeat_pairs", "changed_pairs",
                "old_baselines_unchanged", "independent_windows_per_phase", "popups_per_phase", "accepted"
            )},
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
