"""Prepare pinned official SentencePiece source; NDK r28 builds 16KB-capable JNI.
No remote scripts are executed. The source archive is fixed by commit and SHA256.
"""
import argparse
import hashlib
from pathlib import Path
import tarfile
import urllib.request

REVISION = "31646a467d2051eb904e0b45de3a73e91fe1c1e3"
SHA256 = "b4eb17e6bea5c9380ddd79d04d01d6aa5e280be8f3e3cef1f745a80e6d9451ce"


def prepare(root):
    root.mkdir(parents=True, exist_ok=True)
    archive = root / "sentencepiece-0.2.1.tar.gz"
    if not archive.exists():
        with urllib.request.urlopen(f"https://codeload.github.com/google/sentencepiece/tar.gz/{REVISION}", timeout=60) as response:
            archive.write_bytes(response.read())
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise RuntimeError("SentencePiece archive checksum mismatch")
    source = root / "sources" / f"sentencepiece-{REVISION}"
    with tarfile.open(archive) as tar:
        if not source.exists():
            tar.extractall(root / "sources", filter="data")
        # A modified source checkout must not masquerade as the pinned dependency.
        for member in tar.getmembers():
            if member.isfile():
                actual = root / "sources" / member.name
                if hashlib.sha256(actual.read_bytes()).digest() != hashlib.sha256(tar.extractfile(member).read()).digest():
                    raise RuntimeError(f"Modified pinned source: {member.name}")
    licenses = Path(__file__).resolve().parents[2] / "app/src/main/assets/licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    for name in ["LICENSE", "NOTICE"]:
        if (source / name).is_file():
            (licenses / f"sentencepiece-{name}.txt").write_bytes((source / name).read_bytes())
    for name in ["absl", "protobuf-lite", "darts_clone", "esaxx"]:
        (licenses / f"sentencepiece-{name}-LICENSE.txt").write_bytes((source / "third_party" / name / "LICENSE").read_bytes())
    print(source.as_posix())


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    prepare(parser.parse_args().root)
