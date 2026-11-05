"""整理 P10 真实前后对照；只写任务审阅资料，不写截图基准。"""

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

EXPECTED_CASES = 16
EXPECTED_OLD_PNGS = 110
WINDOW_TARGETS = {"independent-dialog-root", "independent-sheet-root"}


def images_by_id(folder):
    images = {}
    for path in folder.rglob("*.png"):
        name = path.stem.removesuffix("_actual")
        if not name.startswith("E10_") or name.endswith(("_compare", "_diff")):
            continue
        if name in images and support.sha256(images[name]) != support.sha256(path):
            raise SystemExit(f"同一用例存在不同图片：{name}")
        images[name] = path
    return images


def evidence_records(folder, images):
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8-sig"))
    rows = manifest["cases"]
    records = {row["id"]: row for row in rows}
    if len(rows) != EXPECTED_CASES or records.keys() != images.keys():
        raise SystemExit(f"参数清单必须匹配16张E10图：{folder}")
    for name, row in records.items():
        if support.sha256(images[name]) != row["sha256"]:
            raise SystemExit(f"证据哈希不匹配：{folder.name}/{name}")
        with Image.open(images[name]) as picture:
            if list(picture.size) != row["size"]:
                raise SystemExit(f"证据尺寸不匹配：{name}")
    windows = [row for row in rows if row["target"] in WINDOW_TARGETS]
    if len(windows) != 6 or any(not row.get("window") for row in windows):
        raise SystemExit("必须有三类Sheet浅深共6份真实独立窗口证据")
    if any(row["fontScale"] != 2 or row["stageDp"] != [320, 640] for row in windows):
        raise SystemExit("Sheet证据必须来自320×640dp / 200%字体")
    return records


def main():
    frozen = {}
    for line in (TASK / "research/accepted-png-hashes-before-p10.txt").read_text(encoding="utf-8-sig").splitlines():
        digest, relative = line.split("  ", 1)
        frozen[relative] = digest.lower()
    approval_path = TASK / "research/visual-acceptance.json"
    approval = json.loads(approval_path.read_text(encoding="utf-8")) if approval_path.exists() else {}
    accepted = approval.get("accepted") is True
    expected = dict(frozen)
    if accepted:
        if len(approval["candidates"]) != EXPECTED_CASES:
            raise SystemExit("接纳记录必须包含16张E10精确哈希")
        expected.update({
            f"app/src/test/snapshots/SettingsHomeScreenshotE10Test/{name}.png": digest
            for name, digest in approval["candidates"].items()
        })
    current = {
        path.relative_to(ROOT).as_posix(): support.sha256(path)
        for path in (ROOT / "app/src/test/snapshots").rglob("*.png")
    }
    if len(frozen) != EXPECTED_OLD_PNGS or current != expected:
        raise SystemExit("已接纳基准与110张冻结清单或人工接纳记录不符")

    before_dir, after_dir = TASK / "research/before", TASK / "research/after"
    before, after = images_by_id(before_dir), images_by_id(after_dir)
    if len(before) != EXPECTED_CASES or before.keys() != after.keys():
        raise SystemExit(f"需要完整16对图片：before={len(before)} / after={len(after)}")
    before_records = evidence_records(before_dir, before)
    after_records = evidence_records(after_dir, after)
    if accepted and {name: support.sha256(path) for name, path in after.items()} != approval["candidates"]:
        raise SystemExit("迁移后图片不匹配用户接纳记录")
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
        rows.append({
            "id": name,
            "status": "已人工接纳" if accepted else "新增候选，待人工确认",
            "before_size_px": first_size,
            "after_size_px": second_size,
            "before_source": before[name].relative_to(ROOT).as_posix(),
            "after_source": after[name].relative_to(ROOT).as_posix(),
            "before_sha256": support.sha256(before[name]),
            "after_sha256": support.sha256(after[name]),
            "stage_dp": before_records[name]["stageDp"],
            "font_scale": before_records[name]["fontScale"],
            "capture_target": before_records[name]["target"],
            "changed_pixels": changed,
            "changed_ratio": changed / total,
            **paths,
        })
    data = {
        "expected_new_cases": EXPECTED_CASES,
        "complete_pairs": len(rows),
        "changed_pairs": sum(row["changed_pixels"] > 0 for row in rows),
        "old_baselines_unchanged": len(frozen),
        "accepted": accepted,
        "cases": rows,
    }
    (output / "review-data.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    cards = []
    for row in rows:
        name = html.escape(row["id"])
        figures = "".join(
            f'<figure><figcaption>{label}</figcaption><a href="{row[key]}" target="_blank">'
            f'<img src="{row[key]}" loading="lazy" alt="{name} {label}"></a></figure>'
            for key, label in (("before", "迁移前"), ("after", "迁移后"), ("diff", "变化位置（粉色）"))
        )
        cards.append(
            f'<article data-id="{name}"><h2>{name}</h2><p>{row["status"]} · '
            f'变化 {row["changed_pixels"]:,} 像素（{row["changed_ratio"]:.2%}） · '
            f'字体 {row["font_scale"]:.0%}</p><div class="images">{figures}</div></article>'
        )
    acceptance_text = (
        f"已于{html.escape(approval['date'])}由用户接纳"
        if accepted else "新增候选等待人工接纳"
    )
    page = """<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Narra v2.3 设置首页前后对照</title><style>
*{box-sizing:border-box}body{margin:0;background:#edf1ef;color:#243b30;font:16px/1.6 system-ui,sans-serif}
header{padding:18px 24px;background:white;position:sticky;top:0;z-index:1;border-bottom:1px solid #ccd9d0}
h1{font-size:22px;margin:0}header p{margin:6px 0}input{font:inherit;padding:8px 12px;border:1px solid #83998b;border-radius:8px;width:360px;max-width:100%}
main{max-width:1280px;margin:auto;padding:16px}article{background:white;border:1px solid #ccd9d0;border-radius:16px;padding:16px;margin:16px 0}
h2{font-size:16px;overflow-wrap:anywhere;margin:0}.images{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
figure{margin:0}figcaption{margin-bottom:8px}img{width:100%;height:auto;border:1px solid #ccd9d0}article[hidden]{display:none}
@media(max-width:640px){main{padding:6px}article{padding:8px}.images{gap:5px}figcaption{font-size:12px}}
</style><header><h1>Narra v2.3 设置首页前后对照</h1>
<p>16组真实迁移前后截图，__ACCEPTANCE__；110张旧PNG保持不变。点击图片查看原尺寸。</p>
<p>首页三主题、固定动态色与200%字体；主题色调、颜色模式、显示各浅深共6个真实Sheet窗口。</p>
<p>窗口尺寸变化时，差异图只补透明画布对齐；原图不裁切或拉伸。设备读屏、IME、系统栏及物理性能另行验证。</p>
<input id="search" aria-label="按用例筛选" placeholder="筛选 home、narrow、theme、sheet…"></header>
<main>__CARDS__</main><script>
document.getElementById('search').addEventListener('input',e=>{const term=e.target.value.toLowerCase();
document.querySelectorAll('article').forEach(a=>{a.hidden=!a.dataset.id.toLowerCase().includes(term)})});
</script></html>"""
    (output / "index.html").write_text(
        page.replace("__CARDS__", "".join(cards)).replace("__ACCEPTANCE__", acceptance_text),
        encoding="utf-8",
    )
    normal = [row for row in rows if row["font_scale"] == 1]
    large = [row for row in rows if row["font_scale"] != 1]
    support.contact_sheet(normal, output, "main-overview.png")
    support.contact_sheet(large, output, "large-font-and-sheets.png")
    print(json.dumps({key: value for key, value in data.items() if key != "cases"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
