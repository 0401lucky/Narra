"""仅在新隔离工作树恢复Git原始字节并覆盖捕获源；主产品和旧PNG只读校验。"""

import hashlib
import json
from pathlib import Path
import subprocess


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    research = Path(__file__).resolve().parent
    root = next(path for path in research.parents if (path / "app/build.gradle").is_file())
    isolated = (root / "app/build/p06-before-recapture").resolve()
    assert isolated.is_relative_to((root / "app/build").resolve()) and isolated.is_dir()
    baseline = read_json(research / "implementation-baseline-20261106.json")
    original_commit = baseline["head"]
    head = subprocess.check_output(["git", "-C", str(isolated), "rev-parse", "HEAD"], text=True).strip()
    assert head == original_commit
    top = subprocess.check_output(["git", "-C", str(isolated), "rev-parse", "--show-toplevel"], text=True).strip()
    assert Path(top).resolve() == isolated
    extensions = read_json(research / "source-extensions.json")["sources"]
    expected = baseline["source_sha256"] | {name: item["originalSha256"] for name, item in extensions.items()}
    after = read_json(research / "after/manifest.json")
    current_main = {name: digest(root / name) for name in after["sourceSha256"]}
    assert current_main == after["sourceSha256"], "恢复前主工作区产品发生变化"
    accepted = dict(line.split("  ", 1)[::-1] for line in (research / "accepted-png-hashes-at-start.txt").read_text(encoding="utf-8-sig").splitlines())
    assert {path.relative_to(root).as_posix(): digest(path) for path in (root / "app/src/test/snapshots").rglob("*.png")} == accepted
    assert {path.relative_to(isolated).as_posix(): digest(path) for path in (isolated / "app/src/test/snapshots").rglob("*.png")} == accepted
    originals = {}
    for name, expected_sha in sorted(expected.items()):
        target = (isolated / name).resolve()
        assert target.is_relative_to(isolated)
        blob = subprocess.check_output(["git", "-C", str(isolated), "show", f"{original_commit}:{name}"])
        target.write_bytes(blob)
        originals[name] = {"sha256": digest(target), "gitBlobSha256": hashlib.sha256(blob).hexdigest(),
                           "historicalWorkingTreeSha256": expected_sha, "sourceBytePolicy": "exact-original-git-blob"}
    overlay = {}
    for name in after["testSourceSha256"]:
        source, target = root / name, isolated / name
        assert target.resolve().is_relative_to(isolated)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
        overlay[name] = digest(target)
    with (root / "local.properties").open(encoding="utf-8-sig") as source:
        sdk_line = next(line.strip() for line in source if line.startswith("sdk.dir="))
    (isolated / "local.properties").write_text(sdk_line + "\n", encoding="utf-8")
    assert {name: digest(root / name) for name in current_main} == current_main
    proof = {"originalCommit": original_commit, "isolatedWorktree": str(isolated), "originalSources": originals,
             "overlaySources": overlay, "mainProductSourcesAtStart": current_main, "acceptedPngHashesAtStart": accepted,
             "localPropertiesKeys": ["sdk.dir"], "gitWorktreeOptions": {"core.longpaths": "true, command only"},
             "sourceBytePolicy": "exact-original-git-blob; historical mixed-EOL hashes remain in the original manifest"}
    output = research / "capture-runs/recovery-preparation/source-proof.json"
    assert not output.exists(), "既有来源证明不可覆盖"
    output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared original {head}: {len(originals)} Git-backed source files, {len(overlay)} capture overlays, 158 accepted PNGs unchanged.")


if __name__ == "__main__":
    main()
