"""Scan APK members for recognizable credential formats, without printing matches.
A negative result is not a proof that arbitrary or encrypted secrets are absent.
"""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

PATTERNS = {
    "github_fine_grained": rb"github_pat_[A-Za-z0-9_]{20,255}",
    "github_classic": rb"gh[pousr]_[A-Za-z0-9]{30,255}",
    "api_key_sk": rb"(?<![A-Za-z0-9_-])sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{24,255}",
    "google_api_key": rb"AIza[0-9A-Za-z_-]{35}",
    "private_key": rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----",
}


def inspect(apk):
    findings = set()
    total = 0
    members = 0
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            members += 1
            tail = b""
            with archive.open(entry) as stream:
                while block := stream.read(1024 * 1024):
                    total += len(block)
                    data = tail + block
                    for label, pattern in PATTERNS.items():
                        if re.search(pattern, data):
                            findings.add((entry.filename, label))
                    tail = data[-512:]
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"apk": apk.name, "sha256": digest, "members": members,
            "uncompressed_bytes_scanned": total,
            "findings": [{"member": member, "kind": kind} for member, kind in sorted(findings)],
            "scope": "Recognizable plaintext key formats in every ZIP member; no matched secret values emitted."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = inspect(args.apk)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
    raise SystemExit(1 if report["findings"] else 0)
