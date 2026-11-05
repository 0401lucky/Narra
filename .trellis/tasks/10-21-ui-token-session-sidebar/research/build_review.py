"""整理 P05 真实迁移前后候选；只写任务 review 目录，不写生产基准。"""

import hashlib
import html
import json
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def images_by_id(folder):
    result = {}
    for path in folder.rglob("*.png"):
        name = path.stem.removesuffix("_actual")
        if not name.startswith("E05_") or name.endswith(("_compare", "_diff")):
            continue
        if name in result and sha256(result[name]) != sha256(path):
            raise SystemExit(f"同一用例出现不同图片：{name}")
        result[name] = path
    return result


def check_evidence_manifest(folder, images):
    path = folder / "manifest.json"
    if not path.is_file():
        raise SystemExit(f"缺少证据参数清单：{folder}")
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    records = {item["id"]: item for item in data["cases"]}
    if records.keys() != images.keys():
        raise SystemExit(f"证据参数清单与18图不一致：{folder}")
    for name, record in records.items():
        if sha256(images[name]) != record["sha256"]:
            raise SystemExit(f"证据哈希与冻结清单不一致：{folder.name}/{name}")
        with Image.open(images[name]) as picture:
            if list(picture.size) != record["size"]:
                raise SystemExit(f"证据尺寸与参数不一致：{name}")
    if sum(item["target"] == "independent-dialog-root" for item in records.values()) != 5:
        raise SystemExit("必须有5张独立窗口root证据")
    return records


def difference(before, after):
    with Image.open(before) as first, Image.open(after) as second:
        first_size, second_size = first.size, second.size
        size = max(first.width, second.width), max(first.height, second.height)
        canvases = []
        for picture in (first, second):
            canvas = Image.new("RGBA", size, (0, 0, 0, 0))
            canvas.paste(picture.convert("RGBA"), ((size[0] - picture.width) // 2, 0))
            canvases.append(canvas)
        channels = ImageChops.difference(*canvases).split()
        mask = channels[0]
        for channel in channels[1:]:
            mask = ImageChops.lighter(mask, channel)
        mask = mask.point(lambda value: 255 if value else 0)
        overlay = canvases[1].convert("L").convert("RGB")
        overlay.paste(Image.new("RGB", size, "#e82f76"), (0, 0), mask)
        return overlay, mask.histogram()[255], size[0] * size[1], first_size, second_size


def contact_sheet(rows, output, filename):
    cell_width, cell_height = 640, 810
    sheet = Image.new("RGB", (1280, ((len(rows) + 1) // 2) * cell_height), "#edf1ef")
    draw = ImageDraw.Draw(sheet)
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    font = ImageFont.truetype(str(font_path), 17) if font_path.exists() else ImageFont.load_default()
    for index, row in enumerate(rows):
        x, y = (index % 2) * cell_width, (index // 2) * cell_height
        draw.text((x + 12, y + 8), row["id"], fill="#243b30", font=font)
        for column, (key, label) in enumerate((("before", "迁移前"), ("after", "本轮候选"))):
            draw.text((x + 12 + column * 320, y + 38), label, fill="#243b30", font=font)
            with Image.open(output / row[key]) as picture:
                picture = picture.convert("RGB")
                picture.thumbnail((300, 730))
                sheet.paste(picture, (x + column * 320 + (320 - picture.width) // 2, y + 68))
    sheet.save(output / filename)


def main():
    task = Path(__file__).resolve().parent.parent
    root = next(parent for parent in task.parents if (parent / ".trellis").is_dir())
    frozen = {}
    for line in (task / "research/accepted-png-hashes-before-p05.txt").read_text(encoding="utf-8-sig").splitlines():
        digest, path = line.split("  ", 1)
        frozen[path] = digest.lower()
    approval_path = task / "research/visual-acceptance.json"
    approval = json.loads(approval_path.read_text(encoding="utf-8")) if approval_path.exists() else {}
    accepted = approval.get("accepted") is True
    expected = dict(frozen)
    if accepted:
        if len(approval["candidates"]) != 18:
            raise SystemExit("接纳记录必须包含18张E05的精确哈希")
        expected.update({
            f"app/src/test/snapshots/SessionSidebarScreenshotE05Test/{name}.png": digest
            for name, digest in approval["candidates"].items()
        })
    current = {p.relative_to(root).as_posix(): sha256(p) for p in (root / "app/src/test/snapshots").rglob("*.png")}
    if len(frozen) != 92 or current != expected:
        raise SystemExit("已接纳基准与冻结清单或人工接纳记录不符，停止整理")

    before_dir, after_dir = task / "research/before", task / "research/after"
    before, after = images_by_id(before_dir), images_by_id(after_dir)
    if len(before) != 18 or before.keys() != after.keys():
        raise SystemExit(f"需有匹配的18对E05图：before={len(before)} / after={len(after)}")
    before_records = check_evidence_manifest(before_dir, before)
    after_records = check_evidence_manifest(after_dir, after)
    if accepted and {name: sha256(path) for name, path in after.items()} != approval["candidates"]:
        raise SystemExit("迁移后图片不匹配人工接纳记录，停止整理")
    for name in before:
        for key in ("fontScale", "stageDp", "target"):
            if before_records[name][key] != after_records[name][key]:
                raise SystemExit(f"前后捕获参数不一致：{name}/{key}")

    output = task / "review"
    for folder in ("before", "after", "diff"):
        (output / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for name in sorted(before):
        paths = {folder: f"{folder}/{name}.png" for folder in ("before", "after", "diff")}
        shutil.copyfile(before[name], output / paths["before"])
        shutil.copyfile(after[name], output / paths["after"])
        overlay, changed, total, first_size, second_size = difference(before[name], after[name])
        overlay.save(output / paths["diff"])
        rows.append({
            "id": name, "status": "已人工接纳" if accepted else "新增候选，待人工确认",
            "before_size_px": first_size, "after_size_px": second_size,
            "before_source": before[name].relative_to(root).as_posix(),
            "after_source": after[name].relative_to(root).as_posix(),
            "before_sha256": sha256(before[name]), "after_sha256": sha256(after[name]),
            "stage_dp": before_records[name]["stageDp"],
            "font_scale": before_records[name]["fontScale"],
            "capture_target": before_records[name]["target"],
            "changed_pixels": changed, "changed_ratio": changed / total, **paths,
        })
    data = {
        "expected_new_cases": 18, "complete_pairs": len(rows),
        "changed_pairs": sum(row["changed_pixels"] > 0 for row in rows),
        "old_baselines_unchanged": len(frozen), "accepted": accepted, "cases": rows,
    }
    (output / "review-data.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    cards = []
    for row in rows:
        name = html.escape(row["id"])
        figures = "".join(
            f'<figure><figcaption>{label}</figcaption><a href="{row[key]}" target="_blank">'
            f'<img src="{row[key]}" loading="lazy" alt="{name} {label}"></a></figure>'
            for key, label in (("before", "迁移前"), ("after", "本轮候选"), ("diff", "变化位置（粉色）"))
        )
        cards.append(
            f'<article data-id="{name}"><h2>{name}</h2><p>{row["status"]} · 变化 {row["changed_pixels"]:,} 像素'
            f'（{row["changed_ratio"]:.2%}） · 前 {row["before_size_px"][0]}×{row["before_size_px"][1]} px'
            f' / 后 {row["after_size_px"][0]}×{row["after_size_px"][1]} px</p>'
            f'<div class="images">{figures}</div></article>'
        )
    page = """<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Narra v2.3 会话设置侧栏前后对照</title><style>
*{box-sizing:border-box}body{margin:0;background:#edf1ef;color:#243b30;font:16px/1.6 system-ui,sans-serif}
header{padding:18px 24px;background:white;position:sticky;top:0;z-index:1;border-bottom:1px solid #ccd9d0}
h1{font-size:22px;margin:0}header p{margin:6px 0}input{font:inherit;padding:8px 12px;border:1px solid #83998b;border-radius:8px;width:360px;max-width:100%}
main{max-width:1280px;margin:auto;padding:16px}article{background:white;border:1px solid #ccd9d0;border-radius:16px;padding:16px;margin:16px 0}
h2{font-size:16px;overflow-wrap:anywhere;margin:0}.images{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
figure{margin:0}figcaption{margin-bottom:8px}img{width:100%;height:auto;border:1px solid #ccd9d0}article[hidden]{display:none}
@media(max-width:640px){main{padding:6px}article{padding:8px}.images{gap:5px}figcaption{font-size:12px}}
</style><header><h1>Narra v2.3 会话设置侧栏前后对照</h1>
<p>18 对真实迁移前后截图。__ACCEPTANCE__；点击图片查看原尺寸。</p>
<p>窗口尺寸若因内容布局变化，差异图仅补透明画布对齐，原始证据不裁切、不拉伸。</p>
<p>五张弹层捕获独立窗口 root；参数与验证结果见任务 research。设备读屏、IME、系统栏、玻璃与物理性能仍待验证。</p>
<input id="search" aria-label="按用例筛选" placeholder="筛选 main、theme、sheet、dialog…"></header>
<main>__CARDS__</main><script>
document.getElementById('search').addEventListener('input',e=>{const term=e.target.value.toLowerCase();
document.querySelectorAll('article').forEach(a=>{a.hidden=!a.dataset.id.toLowerCase().includes(term)})});
</script></html>"""
    acceptance_text = (
        f"已于{html.escape(approval['date'])}由用户接纳，原有92张PNG保持不变"
        if accepted else "新增候选尚未接纳，原有92张PNG保持不变"
    )
    (output / "index.html").write_text(
        page.replace("__CARDS__", "".join(cards)).replace("__ACCEPTANCE__", acceptance_text),
        encoding="utf-8",
    )
    normal = [row for row in rows if "main" in row["id"] and "narrow" not in row["id"]]
    large = [row for row in rows if row not in normal]
    contact_sheet(normal, output, "main-overview.png")
    contact_sheet(large, output, "large-font-and-windows.png")
    print(json.dumps({key: value for key, value in data.items() if key != "cases"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
