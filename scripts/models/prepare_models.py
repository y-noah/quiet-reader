"""Prepare pinned, public CPU models and native tokenizer graphs; never uses a key.

Large artifacts stay in ignored .tools/model-assets. Model revision/hash manifest is
part of each trial package. Downloading weights alone does not certify Android QA.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / ".tools" / "model-assets" / "models"
MODELS = {
    "embedding": {
        "original": "BAAI/bge-small-zh-v1.5",
        "original_revision": "7999e1d3359715c523056ef9478215996d62a620",
        "quantized": "onnx-community/bge-small-zh-v1.5-ONNX",
        "quantized_revision": "9507db33464b5da99a532ac26b2a251767cbc62b",
        "weights": ["onnx/model_quantized.onnx", "onnx/model_quantized.onnx_data"],
        "tokenizer": ["config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", "vocab.txt"],
    },
    "reranker": {
        "original": "BAAI/bge-reranker-base",
        "original_revision": "2cfc18c9415c912f9d8155881c133215df768a70",
        "quantized": "Xenova/bge-reranker-base",
        "quantized_revision": "280bcc27a84e0b898c251e06fddb25171bd9b101",
        "weights": ["onnx/model_int8.onnx"],
        "tokenizer": ["config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", "sentencepiece.bpe.model"],
    },
}


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def get(url):
    # Public files only: no huggingface login, token, environment credentials, or cookies.
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "QuietReader-ModelPreparation/1.0"}), timeout=60)


def metadata(repo, revision):
    with get(f"https://huggingface.co/api/models/{repo}/revision/{revision}?blobs=true") as response:
        info = json.load(response)
    if info["sha"] != revision:
        raise RuntimeError("Model revision mismatch")
    return {entry["rfilename"]: entry for entry in info["siblings"]}


def fetch(repo, revision, name, target, entry):
    expected_size = entry.get("size")
    expected_hash = entry.get("lfs", {}).get("sha256")
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and (not expected_size or target.stat().st_size == expected_size):
        if expected_hash and digest(target) == expected_hash:
            return
        if not expected_hash and entry.get("blobId"):
            data = target.read_bytes()
            git_hash = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
            if git_hash == entry["blobId"]:
                return
    print(f"Download {repo}@{revision[:8]} {name}", flush=True)
    temporary = target.with_name(target.name + ".partial")
    with get(f"https://huggingface.co/{repo}/resolve/{revision}/{name}?download=true") as response, temporary.open("wb") as output:
        shutil.copyfileobj(response, output, 1024 * 1024)
    if expected_size and temporary.stat().st_size != expected_size:
        raise RuntimeError(f"Size mismatch: {name}")
    if expected_hash:
        if digest(temporary) != expected_hash:
            raise RuntimeError(f"SHA256 mismatch: {name}")
    elif entry.get("blobId"):
        data = temporary.read_bytes()
        if hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest() != entry["blobId"]:
            raise RuntimeError(f"Git blob mismatch: {name}")
    else:
        raise RuntimeError(f"Missing upstream checksum: {name}")
    temporary.replace(target)


def download():
    for kind, spec in MODELS.items():
        weights = metadata(spec["quantized"], spec["quantized_revision"])
        original = metadata(spec["original"], spec["original_revision"])
        for name in spec["weights"]:
            fetch(spec["quantized"], spec["quantized_revision"], name, ASSETS / kind / Path(name).name, weights[name])
        for name in spec["tokenizer"] + ["README.md"]:
            fetch(spec["original"], spec["original_revision"], name, ASSETS / kind / "upstream" / name, original[name])
    print("All pinned files verified", flush=True)


def manifest():
    out = {"schema": 1, "runtime": "onnxruntime-android:1.23.2", "tokenizer_runtime": "sentencepiece:0.2.1+qr-jni-v1", "tokenizer_source_revision": "31646a467d2051eb904e0b45de3a73e91fe1c1e3", "models": []}
    for kind, spec in MODELS.items():
        item = dict(spec, id=kind, license="MIT", files=[])
        for path in sorted((ASSETS / kind).rglob("*")):
            if path.is_file() and not path.name.endswith(".partial"):
                item["files"].append({"path": path.relative_to(ASSETS).as_posix(), "bytes": path.stat().st_size, "sha256": digest(path)})
        out["models"].append(item)
    (ASSETS / "manifest.json").write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Manifest written (no secrets, pinned provenance)", flush=True)


def package_runtime():
    """Whitelist runtime files; upstream reference material is not shipped twice."""
    manifest_data = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
    runtime = dict(manifest_data, models=[])
    allowed = {
        "embedding": ["model_quantized.onnx", "model_quantized.onnx_data", "upstream/vocab.txt"],
        "reranker": ["model_int8.onnx", "upstream/sentencepiece.bpe.model"],
    }
    package = ROOT / ".tools/model-package/models"
    stale = package / "reranker/tokenizer.onnx"
    if stale.exists():
        stale.resolve().relative_to(package.resolve())
        stale.unlink()  # Exact obsolete generated artifact, not an arbitrary directory.
    for model in manifest_data["models"]:
        kind = model["id"]
        files = {f"{kind}/{name}" for name in allowed[kind]}
        selected = [f for f in model["files"] if f["path"] in files]
        if len(selected) != len(files):
            raise RuntimeError("Missing runtime model artifacts")
        runtime["models"].append(dict(model, files=selected))
        for entry in selected:
            target = package / entry["path"]
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists() or digest(target) != entry["sha256"]:
                shutil.copyfile(ASSETS / entry["path"], target)
    data = json.dumps(runtime, ensure_ascii=False, indent=2) + "\n"
    trusted = ROOT / "app/src/main/assets/local-ai-manifest.json"
    trusted.parent.mkdir(parents=True, exist_ok=True)
    trusted.write_text(data, encoding="utf-8")
    (package / "manifest.json").write_text(data, encoding="utf-8")
    print(f"Runtime assets: {sum(f['bytes'] for m in runtime['models'] for f in m['files'])} bytes; packaged manifest is the trust anchor", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--download-only", action="store_true")
    parser.add_argument("--offline", action="store_true", help="Use previously verified local files to generate tokenizers")
    args = parser.parse_args()
    if not args.offline:
        download()
    manifest()
    if not args.download_only:
        package_runtime()
