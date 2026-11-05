"""展示稳定捕获与已接纳图片的差异；不改基准或接纳记录。"""

import html
import importlib.util
import json
import shutil
from pathlib import Path

TASK = Path(__file__).resolve().parent.parent
ROOT = next(p for p in TASK.parents if (p / ".trellis").is_dir())
spec = importlib.util.spec_from_file_location("p11_review", TASK / "research/build_review.py")
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


def main():
    initial_approval = TASK / "research/visual-acceptance-initial-20261105.json"
    approved = json.loads((initial_approval if initial_approval.exists() else TASK / "research/visual-acceptance.json").read_text(encoding="utf-8"))
    assert approved["accepted"] is True
    correction_path = TASK / "research/visual-acceptance-correction-20261105.json"
    correction_accepted = correction_path.exists() and json.loads(correction_path.read_text(encoding="utf-8")).get("accepted") is True
    frozen = {}
    for line in (TASK / "research/accepted-png-hashes-20261105.txt").read_text().splitlines():
        digest, path = line.split("  ", 1)
        frozen[path] = digest
    current = {p.relative_to(ROOT).as_posix(): review.support.sha256(p)
               for p in (ROOT / "app/src/test/snapshots").rglob("*.png")}
    assert current == frozen and len(current) == 158
    stable = TASK / "research/capture-stability-20261105"
    before_manifest, before_records, before = review.evidence(stable / "before")
    after_manifest, after_records, after = review.evidence(stable / "after")
    assert before.keys() == after.keys() == approved["candidates"].keys()
    original = json.loads((TASK / "research/after/manifest.json").read_text(encoding="utf-8"))
    for path, digest in original["sourceSha256"].items():
        assert review.support.sha256(ROOT / path) == digest, path
    originals = {r["id"]: TASK / "research/after" / r["file"] for r in original["cases"]}
    output = TASK / "review-capture-stability"
    for folder in ("previous", "stable", "diff"):
        (output / folder).mkdir(parents=True, exist_ok=True)
    changed = []
    for name in sorted(after):
        for key in ("fontScale", "stageDp", "target"):
            assert before_records[name][key] == after_records[name][key], (name, key)
        assert review.support.sha256(originals[name]) == approved["candidates"][name]
        digest = review.support.sha256(after[name])
        if digest == approved["candidates"][name]:
            continue
        paths = {folder: f"{folder}/{name}.png" for folder in ("previous", "stable", "diff")}
        shutil.copyfile(originals[name], output / paths["previous"])
        shutil.copyfile(after[name], output / paths["stable"])
        diff, count, total, _, _ = review.support.difference(originals[name], after[name])
        diff.save(output / paths["diff"])
        changed.append({"id": name, "label": review.scene_label(name),
                        "previous_sha256": approved["candidates"][name], "stable_sha256": digest,
                        "changed_pixels": count, "changed_ratio": count / total, **paths})
    data = {"accepted": correction_accepted, "production_change": False, "changed_count": len(changed),
            "unchanged_previously_accepted_e11": 32 - len(changed), "baseline_pngs_unchanged": 158 - len(changed) if correction_accepted else 158,
            "before_manifest_sha256": review.support.sha256(stable / "before/manifest.json"),
            "after_manifest_sha256": review.support.sha256(stable / "after/manifest.json"),
            "cases": changed}
    (output / "review-data.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    cards = []
    for row in changed:
        figures = "".join(f'<figure><figcaption>{label}</figcaption><a href="{row[key]}">'
                          f'<img src="{row[key]}" alt="{html.escape(row["label"])} {label}"></a></figure>'
                          for key, label in (("previous", "之前接纳的帧"), ("stable", "稳定捕获候选"), ("diff", "变化位置")))
        cards.append(f'<article><h2>{html.escape(row["label"])}</h2><code>{row["id"]}</code>'
                     f'<p>变化 {row["changed_pixels"]:,} 像素（{row["changed_ratio"]:.2%}）</p>'
                     f'<div class="images">{figures}</div></article>')
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>P11 稳定捕获修正</title>
<style>body{margin:0;background:#edf1ef;color:#243b30;font:16px/1.6 system-ui,sans-serif}
header,main{max-width:1240px;margin:auto;padding:20px}h1{font-size:24px}h2{font-size:18px}
article{background:white;border-radius:16px;padding:16px;margin:20px 0}.images{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
figure{margin:0}img{width:100%;height:auto}code{overflow-wrap:anywhere}</style>
<header><h1>P11 稳定捕获修正</h1><p>产品代码和动画保留；修正截图中模型页签的瞬态水波纹。</p>
<p>__STATUS__；其余 __UNCHANGED__ 张 E11 与之前接纳图片完全一致，无需重复确认。</p>
<p>已在原始提交隔离副本补取同策略 before，前后来源及稳定性证据由任务记录保存。</p></header>
<main>__CARDS__</main></html>'''
    status = f"{len(changed)}张稳定帧已按用户确认接纳，其他{158 - len(changed)}张基准未变" if correction_accepted else f"需要审阅{len(changed)}张修正图，158张现有基准尚未改动"
    (output / "index.html").write_text(page.replace("__STATUS__", status)
                                      .replace("__UNCHANGED__", str(32 - len(changed)))
                                      .replace("__CARDS__", "\n".join(cards)), encoding="utf-8")
    print(json.dumps({k: v for k, v in data.items() if k != "cases"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
