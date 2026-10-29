"""Evaluate the existing relevance reranker on fixed observed event-claim pairs.

This is an offline desktop experiment, not a production equivalence classifier.
No prompt, network, API, new model, user data, or Android package changes.
"""
import argparse
import hashlib
import json
import re
import time
from pathlib import Path


def encode(processor, text):
    ids = [0]
    special = {"<s>": 0, "</s>": 2, "<pad>": 1, "<unk>": 3, "<mask>": 250001}
    def append(segment):
        if segment:
            ids.extend(3 if n == 0 else n + 1 for n in processor.encode(segment, out_type=int))
    offset = 0
    for match in re.finditer(r"<s>|</s>|<unk>|<pad>|<mask>", text):
        segment = text[offset:match.start()]
        append(segment.rstrip() if match.group() == "<mask>" else segment)
        ids.append(special[match.group()])
        offset = match.end()
    append(text[offset:])
    return ids + [2]


def pair_ids(processor, query, text):
    left, right = encode(processor, query)[1:-1], encode(processor, text)[1:-1]
    while len(left) + len(right) + 4 > 256:
        if len(left) > len(right):
            left.pop()
        else:
            right.pop()
    return [0] + left + [2, 2] + right + [2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pairs", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    raw = args.pairs.read_bytes()
    observed = json.loads(raw.decode("utf-8-sig"))
    pairs = observed["pairs"]
    if not pairs or any(p["label"] not in ("same", "different", "uncertain") for p in pairs):
        raise ValueError("Missing or unknown event-claim labels")
    import numpy as np
    import onnxruntime as ort
    import sentencepiece as spm
    from prepare_models import ASSETS, ROOT
    folder = ASSETS / "reranker"
    processor = spm.SentencePieceProcessor(model_file=str(folder / "upstream/sentencepiece.bpe.model"))
    golden = json.loads((ROOT / "app/src/androidTest/assets/local-ai-golden.json").read_text(encoding="utf-8"))
    singles = [e for e in golden["tokenizers"] if e["kind"] == "reranker"]
    if not singles or not golden["reranks"]:
        raise ValueError("No approved tokenizer evidence")
    for example in singles:
        if encode(processor, example["text"]) != example["ids"]:
            raise ValueError("Single-input tokenizer differs from approved fixture")
    for example in golden["reranks"]:
        if pair_ids(processor, example["query"], example["text"]) != example["ids"]:
            raise ValueError("Pair truncation differs from approved fixture")
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.enable_cpu_mem_arena = False
    options.enable_mem_pattern = False
    options.add_session_config_entry("session.disable_prepacking", "1")
    began = time.perf_counter()
    session = ort.InferenceSession(str(folder / "model_int8.onnx"), options, providers=["CPUExecutionProvider"])
    loaded = time.perf_counter() - began
    def score(query, text):
        ids = np.array([pair_ids(processor, query, text)], dtype=np.int64)
        inputs = {"input_ids": ids, "attention_mask": np.ones_like(ids)}
        value = float(session.run(None, inputs)[0].reshape(-1)[0])
        if not np.isfinite(value):
            raise ValueError("Invalid reranker logit")
        return value
    # An independent numerical sanity observation, not a loosened pass criterion.
    oracle_delta = max(abs(score(e["query"], e["text"]) - e["logit"]) for e in golden["reranks"])
    for entry in pairs:
        left, right = entry["left"]["title"], entry["right"]["title"]
        entry["input_tokens"] = len(encode(processor, left)) + len(encode(processor, right))
        entry["used_tokens"] = len(pair_ids(processor, left, right))
        entry["input_complete"] = entry["input_tokens"] == entry["used_tokens"]
        entry["rerank_left_to_right"] = score(left, right)
        entry["rerank_right_to_left"] = score(right, left)
        entry["rerank_min"] = min(entry["rerank_left_to_right"], entry["rerank_right_to_left"])
    same = [p["rerank_min"] for p in pairs if p["label"] == "same"]
    different = [p["rerank_min"] for p in pairs if p["label"] == "different"]
    if not same or not different:
        raise ValueError("Both labeled classes are required")
    result = dict(schema=1, input_sha256=hashlib.sha256(raw).hexdigest(),
                  corpus_sha256=observed["corpus_sha256"],
                  scope="Windows desktop INT8 relevance reranker, both title directions; not event-equivalence probabilities, Android measurements or personal-interest labels",
                  tokenizer_checks=len(singles), pair_tokenizer_checks=len(golden["reranks"]),
                  golden_max_absolute_logit_difference=oracle_delta,
                  model_sha256=hashlib.sha256((folder / "model_int8.onnx").read_bytes()).hexdigest(),
                  sentencepiece_sha256=hashlib.sha256((folder / "upstream/sentencepiece.bpe.model").read_bytes()).hexdigest(),
                  min_same_logit=min(same), max_different_logit=max(different),
                  minimum_direction_separates_labeled_pairs=min(same) > max(different),
                  load_seconds=loaded, total_seconds=time.perf_counter()-began,
                  production_rule_changed=False, pairs=pairs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "pairs"}, ensure_ascii=True))


if __name__ == "__main__":
    main()
