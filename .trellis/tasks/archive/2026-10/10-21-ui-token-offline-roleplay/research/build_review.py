"""整理 P04 真实迁移前后截图供人工确认，不写入生产基准。"""

import argparse
import hashlib
import html
import json
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def images_by_id(folder):
    found = {}
    for path in folder.rglob("*.png"):
        name = path.stem.removesuffix("_actual")
        if not name.startswith("E04_") or path.stem.endswith(("_compare", "_diff")):
            continue
        if name in found and sha256(found[name]) != sha256(path):
            raise SystemExit(f"同一用例出现不同图片：{name}")
        found[name] = path
    return found


def difference(before, after):
    with Image.open(before) as first, Image.open(after) as second:
        if first.size != second.size:
            raise SystemExit(f"迁移前后舞台尺寸不同：{before.name}")
        first, second = first.convert("RGBA"), second.convert("RGBA")
        channels = ImageChops.difference(first, second).split()
        mask = channels[0]
        for channel in channels[1:]:
            mask = ImageChops.lighter(mask, channel)
        mask = mask.point(lambda value: 255 if value else 0)
        overlay = second.convert("L").convert("RGB")
        overlay.paste(Image.new("RGB", second.size, "#e82f76"), (0, 0), mask)
        return overlay, mask.histogram()[255], first.width * first.height


def contact_sheet(rows, output, filename):
    if not rows:
        return
    width, cell_width, cell_height = 1280, 640, 800
    sheet = Image.new("RGB", (width, ((len(rows) + 1) // 2) * cell_height), "#edf1ef")
    draw = ImageDraw.Draw(sheet)
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    font = ImageFont.truetype(str(font_path), 18) if font_path.exists() else ImageFont.load_default()
    for index, row in enumerate(rows):
        x, y = (index % 2) * cell_width, (index // 2) * cell_height
        name = row["id"]
        for line, offset in ((name[:53], 8), (name[53:], 30)):
            draw.text((x + 12, y + offset), line, fill="#243b30", font=font)
        for column, (key, label) in enumerate((("before", "迁移前"), ("after", "本轮候选"))):
            draw.text((x + 16 + column * 320, y + 57), label, fill="#243b30", font=font)
            with Image.open(output / row[key]) as picture:
                picture = picture.convert("RGB")
                picture.thumbnail((300, 700))
                sheet.paste(picture, (x + column * 320 + (320 - picture.width) // 2, y + 90))
    sheet.save(output / filename)


def main():
    task = Path(__file__).resolve().parent.parent
    root = next(parent for parent in task.parents if (parent / ".trellis").is_dir())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, default=task / "research/before")
    parser.add_argument("--after", type=Path, default=task / "research/after")
    parser.add_argument("--output", type=Path, default=task / "review")
    args = parser.parse_args()
    baseline = root / "app/src/test/snapshots"
    frozen = json.loads((task / "research/baseline-sha256-before.json").read_text(encoding="utf-8"))
    current = {p.relative_to(baseline).as_posix(): sha256(p) for p in baseline.rglob("*.png")}
    if current != frozen:
        raise SystemExit("原有接纳基准发生变化，停止整理候选")
    before, after = images_by_id(args.before), images_by_id(args.after)
    if len(before) != 14 or before.keys() != after.keys():
        raise SystemExit(f"需有匹配的 14 对 E04 图：before={len(before)} / after={len(after)}")
    before_manifest = json.loads((args.before / "manifest.json").read_text(encoding="utf-8"))
    if before_manifest.keys() != before.keys() or any(
        sha256(before[name]) != record["sha256"] for name, record in before_manifest.items()
    ):
        raise SystemExit("迁移前证据与冻结清单不符")
    output = args.output.resolve()
    for protected in (baseline.resolve(), args.before.resolve(), args.after.resolve()):
        if output == protected or protected in output.parents:
            raise SystemExit("审阅目录必须独立于基准与原始证据")
    for folder in ("before", "after", "diff"):
        (output / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for name in sorted(before):
        paths = {folder: f"{folder}/{name}.png" for folder in ("before", "after", "diff")}
        shutil.copyfile(before[name], output / paths["before"])
        shutil.copyfile(after[name], output / paths["after"])
        overlay, changed, total = difference(before[name], after[name])
        overlay.save(output / paths["diff"])
        rows.append({
            "id": name,
            "status": "新增候选，待人工确认",
            "before_source": before[name].relative_to(root).as_posix(),
            "after_source": after[name].relative_to(root).as_posix(),
            "before_sha256": sha256(before[name]),
            "after_sha256": sha256(after[name]),
            "changed_pixels": changed,
            "changed_ratio": changed / total,
            **paths,
        })
    data = {
        "expected_new_cases": 14,
        "complete_pairs": len(rows),
        "changed_pairs": sum(row["changed_pixels"] > 0 for row in rows),
        "old_baselines_unchanged": len(frozen),
        "accepted": False,
        "cases": rows,
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
            f'<article data-id="{name}"><h2>{name}</h2><p>待确认 · 变化 {row["changed_pixels"]:,} 像素'
            f'（{row["changed_ratio"]:.2%}）</p><div class="images">{figures}</div></article>'
        )
    page = """<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Narra v2.3 线下扮演与阅读候选</title><style>
*{box-sizing:border-box}body{margin:0;background:#edf1ef;color:#243b30;font:16px/1.6 system-ui,sans-serif}
header{padding:18px 24px;background:white;position:sticky;top:0;z-index:1;border-bottom:1px solid #ccd9d0}
h1{font-size:22px;margin:0}header p{margin:6px 0}input{font:inherit;padding:8px 12px;border:1px solid #83998b;border-radius:8px;width:360px;max-width:100%}
main{max-width:1280px;margin:auto;padding:16px}article{background:white;border:1px solid #ccd9d0;border-radius:16px;padding:16px;margin:16px 0}
h2{font-size:16px;overflow-wrap:anywhere;margin:0}.images{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
figure{margin:0}figcaption{margin-bottom:8px}img{width:100%;height:auto;border:1px solid #ccd9d0}article[hidden]{display:none}
@media(max-width:640px){main{padding:6px}article{padding:8px}.images{gap:5px}figcaption{font-size:12px}}
</style><header><h1>Narra v2.3 线下扮演与会话阅读候选</h1>
<p>14 对迁移前后截图。新增候选尚未接纳，原有 78 张基准保持不变；点击图片查看原尺寸。</p>
<p>本页只审阅界面，真机材质、系统栏、输入法与物理性能另行验证。</p>
<input id="search" aria-label="按用例筛选" placeholder="筛选 reading、narrow、dark…"></header>
<main>__CARDS__</main><script>
document.getElementById('search').addEventListener('input',e=>{const term=e.target.value.toLowerCase();
document.querySelectorAll('article').forEach(a=>{a.hidden=!a.dataset.id.toLowerCase().includes(term)})});
</script></html>"""
    (output / "index.html").write_text(page.replace("__CARDS__", "".join(cards)), encoding="utf-8")
    contact_sheet([row for row in rows if "reading" not in row["id"] and "narrow" not in row["id"]], output, "scene-overview.png")
    contact_sheet([row for row in rows if "reading" in row["id"] or "narrow" in row["id"]], output, "reading-large-font.png")
    print(json.dumps({key: value for key, value in data.items() if key != "cases"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
