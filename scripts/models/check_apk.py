"""Record model APK size/hash and verify 64-bit ELF LOAD alignment for 16KB pages.
This is a binary prerequisite, not proof of runtime behavior on a 16KB ARM device.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


def main(apk, output, model_manifest=None):
    report = {"apk": apk.name, "bytes": apk.stat().st_size, "sha256": hashlib.file_digest(apk.open("rb"), "sha256").hexdigest(), "elf": [], "failures": []}
    with zipfile.ZipFile(apk) as archive:
        report["bundled_model_bytes"] = sum(i.file_size for i in archive.infolist() if i.filename.startswith("assets/models/"))
        if model_manifest is not None:
            expected_manifest = model_manifest.read_bytes()
            packaged_manifest = archive.read("assets/local-ai-manifest.json")
            report["model_manifest_sha256"] = hashlib.sha256(packaged_manifest).hexdigest()
            if packaged_manifest != expected_manifest:
                report["failures"].append("Packaged model manifest differs from supplied source manifest")
            report["model_files"] = []
            expected_paths = set()
            names = archive.namelist()
            # prepare_models.py also packages a copy beside the payloads.
            bundled_manifest = "assets/models/manifest.json"
            if bundled_manifest in names:
                expected_paths.add(bundled_manifest)
                if names.count(bundled_manifest) != 1 or archive.read(bundled_manifest) != expected_manifest:
                    report["failures"].append("Bundled model directory manifest differs from supplied source manifest")
            for model in json.loads(expected_manifest)["models"]:
                for entry in model["files"]:
                    name = "assets/models/" + entry["path"]
                    expected_paths.add(name)
                    if names.count(name) != 1:
                        report["failures"].append("Missing or duplicate model member: " + name)
                        continue
                    with archive.open(name) as stream:
                        digest = hashlib.file_digest(stream, "sha256").hexdigest()
                    size = archive.getinfo(name).file_size
                    valid = size == entry["bytes"] and digest == entry["sha256"]
                    report["model_files"].append({"path": name, "bytes": size, "sha256": digest, "valid": valid})
                    if not valid:
                        report["failures"].append("Model size/hash mismatch: " + name)
            unexpected = sorted(name for name in names if name.startswith("assets/models/") and not name.endswith("/") and name not in expected_paths)
            report["failures"].extend("Unexpected model member: " + name for name in unexpected)
        for name in archive.namelist():
            if not name.endswith(".so") or not name.startswith(("lib/arm64-v8a/", "lib/x86_64/")):
                continue
            data = archive.read(name)
            if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
                raise ValueError("Expected 64-bit little-endian ELF")
            offset = struct.unpack_from("<Q", data, 32)[0]
            size, count = struct.unpack_from("<HH", data, 54)
            alignments = []
            for i in range(count):
                values = struct.unpack_from("<IIQQQQQQ", data, offset + i * size)
                if values[0] == 1:
                    alignment = values[7]
                    alignments.append(alignment)
                    if alignment < 16384 or values[2] % 16384 != values[3] % 16384:
                        report["failures"].append(name)
            report["elf"].append({"path": name, "load_alignments": alignments})
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
    if report["failures"]:
        raise SystemExit("APK verification failure; see report")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model-manifest", type=Path, help="Verify packaged manifest and every bundled model file against this source manifest")
    args = parser.parse_args()
    main(args.apk, args.output, args.model_manifest)
