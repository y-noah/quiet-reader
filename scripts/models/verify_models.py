"""Offline tokenizer parity + desktop INT8 oracle for Android integration tests.

Synthetic sentences test numerical/format correctness, NOT personal recommendation
quality. Desktop timings are not Android battery/performance measurements.
"""
import gc
import json
import os
from pathlib import Path
import time
import re

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
import numpy as np
import onnxruntime as ort
import sentencepiece as spm
from transformers import AutoTokenizer
from prepare_models import ASSETS, ROOT

TEXTS = [
    "普通人使用 AI 提高工作效率的真实经历",
    "某品牌新手机发布会宣布明星代言",
    "家庭 NAS 备份如何避免硬盘故障导致数据丢失？",
    "繁體中文：家庭網路與資料備份。",
    "Android 15 / iPhone 16：Wi-Fi 7、USB-C 与 5G",
    "租房合同里的押金、维修责任和提前退租条款",
    "\t换行\n多个  空格\u3000全角字符ＡＢＣ１２３",
    "咖啡 café、cafe\u0301；emoji 👩‍💻🛰️与标点！",
    "", "a", "？", "[CLS] 来源标题 [SEP]", "<s>来源正文</s>",
    "测试长标题与摘要" * 80,
    "a[UNK]b [MASK][PAD] [SEP][CLS]",
    "<pad>标题<unk>内容  \t<mask> 空格<s></s>",
    "中\u0000文\u0001控\u200b制\ue000字\u0378符\ufffd",
    "中文\u0001控\u200b制\ue000字\u0378符\ufffd",
    "\u00a0\u2007标题\u2028第二行\u2029最后\u0085一行",
    "naïve Ångström résumé 中文𠀀",
]
PAIRS = [
    ("家庭数据备份的实际经验", "NAS 硬盘损坏后如何恢复照片：一次真实的恢复记录"),
    ("家庭数据备份的实际经验", "某明星出席红毯展示新造型"),
    ("手机实际使用体验", "新手机用了一个月，续航、发热和拍照有哪些问题？"),
    ("手机实际使用体验", "品牌官宣新代言人，发布会将于下周举办"),
    ("中文" * 160, "测试" * 160),
]

def native_sp(session, text):
    """Match HF's added-token splitting around native SentencePiece segments."""
    tokens = [0]
    def append(segment):
        if segment:
            tokens.extend(3 if token == 0 else token + 1 for token in session.encode(segment, out_type=int))
    special = {"<s>": 0, "</s>": 2, "<pad>": 1, "<unk>": 3, "<mask>": 250001}
    offset = 0
    for match in re.finditer(r"<s>|</s>|<unk>|<pad>|<mask>", text):
        segment = text[offset:match.start()]
        append(segment.rstrip() if match.group() == "<mask>" else segment)
        tokens.append(special[match.group()])
        offset = match.end()
    append(text[offset:])
    return tokens + [2]


def options():
    opt = ort.SessionOptions()
    opt.intra_op_num_threads = 2
    opt.inter_op_num_threads = 1
    opt.enable_cpu_mem_arena = False
    opt.enable_mem_pattern = False
    opt.add_session_config_entry("session.disable_prepacking", "1")
    return opt


def open_session(path):
    return ort.InferenceSession(str(path), options(), providers=["CPUExecutionProvider"])


def main():
    fixture = {"schema": 1, "description": "Synthetic parity fixtures. No personal data; not recommendation-quality evidence.", "tokenizers": [], "embeddings": [], "reranks": []}
    report = {"device": "Windows x86_64 desktop (NOT Android)", "checks": 0, "failures": [], "models": {}}
    for kind in ["embedding", "reranker"]:
        folder = ASSETS / kind
        hf = AutoTokenizer.from_pretrained(str(folder / "upstream"), local_files_only=True, use_fast=False)
        session = spm.SentencePieceProcessor(model_file=str(folder / "upstream/sentencepiece.bpe.model")) if kind == "reranker" else None
        for text in TEXTS:
            expected = hf(text, add_special_tokens=True)["input_ids"]
            if session is not None:
                report["checks"] += 1
                actual = native_sp(session, text)
                if actual != expected:
                    report["failures"].append({"kind": kind, "text": text[:80], "expected": expected[:60], "actual": actual[:60]})
            fixture["tokenizers"].append({"kind": kind, "text": text, "ids": expected})
        del session
        gc.collect()
        weight = "model_quantized.onnx" if kind == "embedding" else "model_int8.onnx"
        started = time.perf_counter()
        session = open_session(folder / weight)
        report["models"][kind] = {"load_seconds": time.perf_counter() - started, "inputs": [i.name for i in session.get_inputs()], "outputs": [o.name for o in session.get_outputs()]}
        if kind == "embedding":
            for text in TEXTS[:4]:
                encoded = hf(text, max_length=128, truncation=True, return_tensors="np")
                inputs = {i.name: encoded[i.name] for i in session.get_inputs()}
                output = session.run(None, inputs)[0]
                vector = output[0, 0] if output.ndim == 3 else output[0]
                vector = vector / np.linalg.norm(vector)
                if vector.shape != (512,) or not np.all(np.isfinite(vector)):
                    raise RuntimeError("Invalid embedding")
                fixture["embeddings"].append({"text": text, "vector": vector.tolist()})
        else:
            for query, text in PAIRS:
                encoded = hf(query, text, max_length=256, truncation=True, return_tensors="np")
                output = session.run(None, {i.name: encoded[i.name] for i in session.get_inputs()})[0]
                fixture["reranks"].append({"query": query, "text": text, "ids": encoded["input_ids"][0].tolist(), "logit": float(output.reshape(-1)[0])})
        del session, hf
        gc.collect()
    evidence = ROOT / "artifacts" / "local-ai"
    evidence.mkdir(parents=True, exist_ok=True)
    report["embedding_tokenizer_note"] = "Java WordPiece parity is checked on Android, not by this Python script; the incompatible native BERT tokenizer is not used."
    report["tokenizer_runtime"] = "Official SentencePiece 0.2.1; Android JNI must preserve standard UTF-8 including embedded NUL. Former ORT Extensions NUL rejection is no longer needed."
    (evidence / "desktop-parity.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    fixture_path = ROOT / "app" / "src" / "androidTest" / "assets" / "local-ai-golden.json"
    fixture_path.parent.mkdir(parents=True, exist_ok=True)
    fixture_path.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True, indent=2), flush=True)
    if report["failures"]:
        raise SystemExit("Native tokenizer parity failed; do not claim model readiness")


if __name__ == "__main__":
    main()
