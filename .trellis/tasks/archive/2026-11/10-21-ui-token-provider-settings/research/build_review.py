"""整理 P11 真实前后证据；只写任务 review，不写接纳基准。"""

import html
import importlib.util
import json
import shutil
from pathlib import Path

from PIL import Image


TASK = Path(__file__).resolve().parent.parent
ROOT = next(parent for parent in TASK.parents if (parent / ".trellis").is_dir())
SUPPORT = ROOT / ".trellis/tasks/archive/2026-11/10-21-ui-token-session-sidebar/research/build_review.py"
support_spec = importlib.util.spec_from_file_location("p05_review_support", SUPPORT)
support = importlib.util.module_from_spec(support_spec)
support_spec.loader.exec_module(support)

EXPECTED_CASES = 32
EXPECTED_OLD_PNGS = 126
EXPECTED_WINDOWS = 12
WINDOW_TARGETS = {"independent-dialog-root", "independent-sheet-root"}
SCENE_LABELS = {
    "list-matcha": "提供商列表 · 抹茶",
    "list-vanilla": "提供商列表 · 香草",
    "list-ocean": "提供商列表 · 海洋",
    "config": "Config 配置表单",
    "models": "Models 模型列表",
    "config-dynamic": "Config · 固定动态色",
    "list-narrow": "窄屏大字体 · 列表末项",
    "config-save-narrow": "窄屏大字体 · 保存区",
    "models-tail-narrow": "窄屏大字体 · 模型末项",
    "template-dialog": "选择提供商模板",
    "fetched-sheet": "抓取后的模型选择",
    "manual-error-dialog": "手工添加 · 空ID错误",
    "ability-dialog": "覆盖模型能力",
    "clear-dialog": "清空模型确认",
    "protocol-dialog": "协议说明",
    "list-no-result": "提供商搜索无结果",
    "models-empty": "模型空态",
}


def scene_label(case_id):
    scene, shade = case_id.removeprefix("E11_").rsplit("-", 1)
    return f"{SCENE_LABELS[scene]} · {'浅色' if shade == 'light' else '深色'}"


def evidence(folder):
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8-sig"))
    rows = manifest["cases"]
    records = {row["id"]: row for row in rows}
    if len(rows) != EXPECTED_CASES or len(records) != EXPECTED_CASES:
        raise SystemExit(f"必须有32个不重复E11场景：{folder}")
    images = {}
    for name, row in records.items():
        if not name.startswith("E11_"):
            raise SystemExit(f"非E11场景：{name}")
        path = (folder / row["file"]).resolve()
        if not path.is_relative_to(folder.resolve()) or not path.is_file():
            raise SystemExit(f"缺少本阶段图片：{name}")
        if support.sha256(path) != row["sha256"]:
            raise SystemExit(f"证据哈希不匹配：{folder.name}/{name}")
        with Image.open(path) as picture:
            if list(picture.size) != row["size"]:
                raise SystemExit(f"证据尺寸不匹配：{name}")
        images[name] = path
    windows = [row for row in rows if row["target"] in WINDOW_TARGETS]
    if len(windows) != EXPECTED_WINDOWS or any(not row.get("window") for row in windows):
        raise SystemExit("必须有六类弹层浅深共12份真实独立窗口证据")
    if any(row["fontScale"] != 2 or row["stageDp"] != [320, 640] for row in windows):
        raise SystemExit("弹层必须来自320×640dp / 200%字体")
    pages = [row for row in rows if row["target"] not in WINDOW_TARGETS]
    if sum(row["fontScale"] == 2 for row in pages) != 6:
        raise SystemExit("必须有列表、Config保存区、Models末项的六张窄屏图")
    for row in pages:
        expected_dp = [320, 640] if row["fontScale"] == 2 else [360, 800]
        if row["stageDp"] != expected_dp or row["size"] != [value * 2 for value in expected_dp]:
            raise SystemExit(f"整页捕获尺寸或字体不符：{row['id']}")
    return manifest, records, images


def frozen_baselines():
    frozen = {}
    path = TASK / "research/accepted-png-hashes-before-p11.txt"
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        digest, relative = line.split("  ", 1)
        frozen[relative] = digest.lower()
    approval_path = TASK / "research/visual-acceptance.json"
    approval = json.loads(approval_path.read_text(encoding="utf-8")) if approval_path.exists() else {}
    accepted = approval.get("accepted") is True
    expected = dict(frozen)
    if accepted:
        if len(approval["candidates"]) != EXPECTED_CASES:
            raise SystemExit("接纳记录必须包含32张E11精确哈希")
        expected.update({
            f"app/src/test/snapshots/ProviderSettingsScreenshotE11Test/{name}.png": digest
            for name, digest in approval["candidates"].items()
        })
    current = {
        image.relative_to(ROOT).as_posix(): support.sha256(image)
        for image in (ROOT / "app/src/test/snapshots").rglob("*.png")
    }
    if len(frozen) != EXPECTED_OLD_PNGS or current != expected:
        raise SystemExit("已接纳PNG与126张冻结清单或人工接纳记录不符")
    return frozen, approval, accepted


def main():
    frozen, approval, accepted = frozen_baselines()
    capture_root = TASK / "research"
    if approval.get("captureRevision"):
        assert approval["captureRevision"] == "capture-stability-20261105"
        capture_root = capture_root / approval["captureRevision"]
    before_manifest, before_records, before = evidence(capture_root / "before")
    after_manifest, after_records, after = evidence(capture_root / "after")
    if before.keys() != after.keys():
        raise SystemExit("前后场景ID不一致")
    if accepted and {name: support.sha256(path) for name, path in after.items()} != approval["candidates"]:
        raise SystemExit("after与人工接纳的精确哈希不一致")
    for name in before:
        for key in ("fontScale", "stageDp", "target"):
            if before_records[name][key] != after_records[name][key]:
                raise SystemExit(f"前后捕获参数不一致：{name}/{key}")

    output = TASK / "review"
    for folder in ("before", "after", "diff"):
        (output / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for name in sorted(before):
        paths = {folder: f"{folder}/{name}.png" for folder in ("before", "after", "diff")}
        shutil.copyfile(before[name], output / paths["before"])
        shutil.copyfile(after[name], output / paths["after"])
        overlay, changed, total, first_size, second_size = support.difference(before[name], after[name])
        overlay.save(output / paths["diff"])
        record = before_records[name]
        group = "window" if record["target"] in WINDOW_TARGETS else "large" if record["fontScale"] == 2 else "regular"
        rows.append({
            "id": name,
            "label": scene_label(name),
            "group": group,
            "status": "已人工接纳" if accepted else "新增候选，待人工确认",
            "before_size_px": first_size,
            "after_size_px": second_size,
            "before_source": before[name].relative_to(ROOT).as_posix(),
            "after_source": after[name].relative_to(ROOT).as_posix(),
            "before_sha256": support.sha256(before[name]),
            "after_sha256": support.sha256(after[name]),
            "stage_dp": record["stageDp"],
            "font_scale": record["fontScale"],
            "capture_target": record["target"],
            "before_window": record.get("window"),
            "after_window": after_records[name].get("window"),
            "before_measurements": record.get("measurements"),
            "after_measurements": after_records[name].get("measurements"),
            "changed_pixels": changed,
            "changed_ratio": changed / total,
            **paths,
        })
    data = {
        "expected_new_cases": EXPECTED_CASES,
        "complete_pairs": len(rows),
        "changed_pairs": sum(row["changed_pixels"] > 0 for row in rows),
        "old_baselines_unchanged": len(frozen),
        "independent_windows_per_phase": EXPECTED_WINDOWS,
        "before_source_sha256": before_manifest["sourceSha256"],
        "supplementary_before_source": json.loads((TASK / "research/shared-dialog-baseline-20261105.json").read_text(encoding="utf-8")),
        "after_source_sha256": after_manifest["sourceSha256"],
        "accepted": accepted,
        "capture_revision": approval.get("captureRevision", "initial"),
        "cases": rows,
    }
    (output / "review-data.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    cards = []
    group_labels = {"regular": "常规页面", "large": "窄屏大字体", "window": "真实弹层"}
    for row in rows:
        name = html.escape(row["id"])
        label = html.escape(row["label"])
        figures = "".join(
            f'<figure><figcaption>{label}</figcaption><a href="{row[key]}" target="_blank">'
            f'<img src="{row[key]}" loading="lazy" alt="{name} {label}"></a></figure>'
            for key, label in (("before", "迁移前"), ("after", "迁移后"), ("diff", "变化位置（粉色）"))
        )
        measurements = html.escape(
            f"迁移前：\n{row['before_window'] or ''}{row['before_measurements'] or ''}\n\n"
            f"迁移后：\n{row['after_window'] or ''}{row['after_measurements'] or ''}"
        )
        evidence_details = (
            f"<details><summary>窗口与操作区捕获证据</summary><pre>{measurements}</pre></details>"
            if row["group"] != "regular" else ""
        )
        cards.append(
            f'<article data-id="{name} {label}" data-group="{row["group"]}"><h2>{label}</h2><code>{name}</code><p>'
            f'{row["status"]} · {group_labels[row["group"]]} · '
            f'变化 {row["changed_pixels"]:,} 像素（{row["changed_ratio"]:.2%}） · '
            f'字体 {row["font_scale"]:.0%}</p>{evidence_details}<div class="images">{figures}</div></article>'
        )
    acceptance_text = f"已于{html.escape(approval['date'])}由用户接纳" if accepted else "新增候选等待人工接纳"
    page = """<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Narra v2.3 提供商配置前后对照</title><style>
*{box-sizing:border-box}body{margin:0;background:#edf1ef;color:#243b30;font:16px/1.6 system-ui,sans-serif}
header{padding:18px 24px;background:white;border-bottom:1px solid #ccd9d0}h1{font-size:22px;margin:0}header p{margin:6px 0}
input,select{font:inherit;padding:8px 12px;min-height:48px;border:1px solid #83998b;border-radius:8px;max-width:100%}
input{width:360px}main{max-width:1280px;margin:auto;padding:16px}
article{background:white;border:1px solid #ccd9d0;border-radius:16px;padding:16px;margin:16px 0}
h2{font-size:16px;overflow-wrap:anywhere;margin:0}.images{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
details{margin:8px 0 16px}summary{cursor:pointer;min-height:48px;padding:10px 0}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px}
figure{margin:0}figcaption{margin-bottom:8px}img{width:100%;height:auto;border:1px solid #ccd9d0}article[hidden]{display:none}
@media(max-width:640px){main{padding:6px}article{padding:8px}.images{gap:5px}figcaption{font-size:12px}}
</style><header><h1>Narra v2.3 提供商配置前后对照</h1>
<p>32组真实迁移前后截图，__ACCEPTANCE__；126张旧PNG保持不变。点击图片查看原尺寸。</p>
<p>提供商列表、Config / Models、固定动态色、320×640dp / 200%字体，以及六类弹层浅深共12个真实窗口。</p>
<p>窗口尺寸变化时，差异图仅补透明画布对齐，原图不裁切或拉伸。设备、物理性能和新视觉远端CI证据见验证记录。</p>
<p><a href="../verification.md">验证记录</a> · <a href="review-data.json">完整参数与SHA-256</a></p>
<label for="search">筛选用例 </label><input id="search" placeholder="config、models、narrow、dialog…">
<label for="group">场景 </label><select id="group"><option value="">全部32组</option value="regular">常规页面</option>
<option value="large">窄屏大字体</option><option value="window">真实弹层</option></select></header>
<main>__CARDS__</main><script>
const search=document.getElementById('search'),group=document.getElementById('group');
function filter(){const term=search.value.toLowerCase();document.querySelectorAll('article').forEach(a=>{
a.hidden=!a.dataset.id.toLowerCase().includes(term)||(group.value&&a.dataset.group!==group.value);});}
search.addEventListener('input',filter);group.addEventListener('change',filter);
</script></html>"""
    (output / "index.html").write_text(page.replace("__CARDS__", "\n".join(cards)).replace("__ACCEPTANCE__", acceptance_text), encoding="utf-8")
    for group in ("regular", "large", "window"):
        selected = [row for row in rows if row["group"] == group]
        for start in range(0, len(selected), 6):
            support.contact_sheet(selected[start:start + 6], output, f"{group}-{start // 6 + 1}.png")
    print(json.dumps({key: value for key, value in data.items() if key not in ("cases", "before_source_sha256", "after_source_sha256", "supplementary_before_source")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
