"""从 verify 的实际输出生成本地审阅材料，不修改已确认截图基准。"""

import argparse
import hashlib
import html
import json
import shutil
import xml.etree.ElementTree as ElementTree
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def image_difference(before, after):
    first = Image.open(before).convert("RGB")
    second = Image.open(after).convert("RGB")
    width, height = max(first.width, second.width), max(first.height, second.height)
    canvas_first = Image.new("RGB", (width, height), "white")
    canvas_second = Image.new("RGB", (width, height), "white")
    canvas_first.paste(first, (0, 0))
    canvas_second.paste(second, (0, 0))
    difference = ImageChops.difference(canvas_first, canvas_second)
    red, green, blue = difference.split()
    mask = ImageChops.lighter(ImageChops.lighter(red, green), blue).point(
        lambda value: 255 if value else 0
    )
    changed = mask.histogram()[255]
    # 灰色原图上标出真实变化，便于同时定位尺寸与颜色差异。
    overlay = canvas_second.convert("L").convert("RGB")
    overlay.paste(Image.new("RGB", overlay.size, "#e82f76"), (0, 0), mask)
    return overlay, changed, width * height


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--actual-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--test-results", type=Path)
    parser.add_argument("--comparison-result", type=Path, help="持久保存的本轮 verify 结果，可在后续测试覆盖 XML 后使用")
    parser.add_argument("--since", type=float, default=0, help="只使用本轮开始后写入的 actual 输出")
    args = parser.parse_args()
    root = args.root.resolve()
    task = root / ".trellis/tasks/10-21-ui-token-accessibility-pilot"
    baseline = root / "app/src/test/snapshots"
    original_hashes = json.loads(
        (task / "research/baseline-sha256-before.json").read_text(encoding="utf-8")
    )
    originals = {p.relative_to(baseline).as_posix(): p for p in baseline.rglob("*.png")}
    if {name: sha256(p) for name, p in originals.items()} != original_hashes:
        raise SystemExit("原始基准发生变化，停止生成审阅材料")

    actuals = {}
    for path in args.actual_root.resolve().rglob("*.png"):
        if path.stat().st_mtime < args.since:
            continue
        if path.stem.endswith("_actual"):
            name = path.stem.removesuffix("_actual")
        elif path.stem.endswith(("_compare", "_diff")):
            continue
        else:
            name = path.stem
        if name in actuals and sha256(actuals[name]) != sha256(path):
            raise SystemExit(f"同一用例有不同实际图，需先确定本轮输出: {name}")
        actuals[name] = path

    verified_suites = set()
    verified_methods = set()
    verified_baselines = set()
    if args.comparison_result:
        result = json.loads(args.comparison_result.read_text(encoding="utf-8"))
        verified_baselines.update(result.get("pixel_unchanged_baselines", []))
    if args.test_results:
        for path in args.test_results.glob("TEST-*.xml"):
            if path.stat().st_mtime < args.since:
                continue
            suite = ElementTree.parse(path).getroot()
            suite_name = suite.attrib["name"].rsplit(".", 1)[-1]
            tests = int(suite.attrib.get("tests", 0))
            if tests and not any(int(suite.attrib.get(key, 0)) for key in ("failures", "errors", "skipped")):
                verified_suites.add(suite_name)
            for case in suite.findall("testcase"):
                if not any(case.find(key) is not None for key in ("failure", "error", "skipped")):
                    verified_methods.add((suite_name, case.attrib["name"]))

    output = args.output.resolve()
    if output == baseline or baseline in output.parents:
        raise SystemExit("审阅输出必须位于基准目录以外")
    for folder in ("before", "after", "diff"):
        (output / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for relative, before in sorted(originals.items()):
        after = actuals.get(before.stem)
        unchanged_verified = relative in verified_baselines or before.parent.name in verified_suites or (
            before.parent.name, before.stem.lower().replace("-", "_")
        ) in verified_methods
        actual_source = str(after.relative_to(root)) if after and after.is_relative_to(root) else after.name if after else None
        if after is None and unchanged_verified:
            after = before
            actual_source = "本轮 verify 通过，像素与原基准一致；展示原基准副本"
        if after is None:
            rows.append({"id": before.stem, "baseline": relative, "status": "未取得本轮实际图"})
            continue
        names = {folder: f"{folder}/{before.name}" for folder in ("before", "after", "diff")}
        shutil.copyfile(before, output / names["before"])
        shutil.copyfile(after, output / names["after"])
        overlay, count, total = image_difference(before, after)
        overlay.save(output / names["diff"])
        rows.append({
            "id": before.stem,
            "family": before.stem.split("_", 1)[0],
            "baseline": relative,
            "actual_source": actual_source,
            "status": "待人工确认" if count else "像素一致",
            "changed_pixels": count,
            "changed_ratio": count / total,
            **names,
        })
    valid = [row for row in rows if "after" in row]
    missing = [row["id"] for row in rows if "after" not in row]
    summary = {
        "original_baseline_count": len(originals),
        "actual_count": len(valid),
        "changed_count": sum(row["changed_pixels"] > 0 for row in valid),
        "missing": missing,
        "baseline_hashes_unchanged": True,
        "cases": rows,
    }
    (output / "review-data.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    cards = []
    for row in rows:
        name = html.escape(row["id"])
        if "after" not in row:
            cards.append(f'<article data-id="{name}"><h2>{name}</h2><p>未取得本轮实际图</p></article>')
            continue
        figures = "".join(
            f'<figure><figcaption>{label}</figcaption><a href="{row[key]}" target="_blank">'
            f'<img loading="lazy" src="{row[key]}" alt="{name} {label}"></a></figure>'
            for key, label in (("before", "原基准"), ("after", "本轮候选"), ("diff", "变化位置（粉色）"))
        )
        status = html.escape(row["status"])
        cards.append(
            f'<article data-id="{name}" data-changed="{int(row["changed_pixels"] > 0)}">'
            f'<h2>{name}</h2><p>{status} · 变化 {row["changed_pixels"]:,} 像素'
            f'（{row["changed_ratio"]:.2%}）</p><div class="images">{figures}</div></article>'
        )
    body = """<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Narra v2.3 统一界面候选审阅</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#f0f3f5;color:#20302b;font:16px/1.6 system-ui,sans-serif}
header{position:sticky;top:0;background:#fffffff0;padding:16px 24px;backdrop-filter:blur(12px);z-index:1;border-bottom:1px solid #d5dfd8}
h1{font-size:22px;margin:0}header p{margin:6px 0}input{font:inherit;padding:8px 12px;border:1px solid #8b9d95;border-radius:8px;max-width:70vw}
main{max-width:1250px;margin:auto;padding:20px}article{background:white;border:1px solid #d5dfd8;border-radius:16px;padding:20px;margin:16px 0}
h2{font-size:17px;overflow-wrap:anywhere;margin:0}article p{color:#57685f}.images{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
figure{margin:0}figcaption{margin-bottom:8px}img{display:block;width:100%;height:auto;border:1px solid #d5dfd8}label{margin-left:16px}article[hidden]{display:none}
@media(max-width:640px){main{padding:8px}article{padding:10px}.images{gap:5px}figcaption{font-size:12px}label{display:block;margin:8px 0 0}}
</style><header><h1>Narra v2.3 统一界面候选审阅</h1>
<p>原始基准哈希保持不变；以下候选尚未接纳为新基准。可点击图片查看原始尺寸。</p>
<p>__COUNTS__</p><input id="search" aria-label="按用例筛选" placeholder="筛选 H04、P03、dark、皮肤名称…"><label><input id="changes" type="checkbox">只显示变化</label></header>
<main>__CARDS__</main><script>
const search=document.getElementById('search'),changes=document.getElementById('changes');
function filter(){const term=search.value.toLowerCase();document.querySelectorAll('article').forEach(a=>{a.hidden=!a.dataset.id.toLowerCase().includes(term)||(changes.checked&&a.dataset.changed!=='1')})}
search.addEventListener('input',filter);changes.addEventListener('change',filter);
</script></html>"""
    counts = f'基准 {len(originals)} 张 · 已取得实际图 {len(valid)} 张 · 变化 {summary["changed_count"]} 张 · 缺图 {len(missing)} 张'
    (output / "index.html").write_text(
        body.replace("__COUNTS__", counts).replace("__CARDS__", "".join(cards)), encoding="utf-8"
    )

    selected = [row for row in valid if row["id"].startswith("H04_") and row["id"].endswith("light")]
    font_path = Path("C:/Windows/Fonts/msyh.ttc")
    font = ImageFont.truetype(str(font_path), 22) if font_path.exists() else ImageFont.load_default()
    if selected:
        sheet = Image.new("RGB", (1440, len(selected) * 940), "#ecf0ee")
        draw = ImageDraw.Draw(sheet)
        for index, row in enumerate(selected):
            y = index * 940
            draw.text((24, y + 10), row["id"], font=font, fill="#243b30")
            for column, key in enumerate(("before", "after", "diff")):
                draw.text((column * 480 + 24, y + 45), {"before": "原基准", "after": "本轮候选", "diff": "变化位置"}[key], font=font, fill="#243b30")
                with Image.open(output / row[key]) as pic:
                    pic = pic.convert("RGB")
                    pic.thumbnail((430, 840))
                    sheet.paste(pic, (column * 480 + (480 - pic.width) // 2, y + 85))
        sheet.save(output / "large-font-comparison.png")
    print(json.dumps({key: value for key, value in summary.items() if key != "cases"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
