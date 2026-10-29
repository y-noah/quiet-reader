"""Compare pinned community FP32/INT8 exports on public synthetic fixtures.

This is a quantization smoke test, not original PyTorch export parity or a
personal-interest benchmark. Reference weights never enter the Android package.
"""
import argparse
import gc
import json
import numpy as np
from transformers import AutoTokenizer
from prepare_models import ROOT, ASSETS, MODELS, metadata, fetch, digest
from verify_models import TEXTS, PAIRS, open_session


def main(offline):
    reference = ROOT / ".tools" / "model-reference"
    report = {"scope": __doc__, "models": {}}
    for kind, spec in MODELS.items():
        folder = reference / kind
        if not offline:
            entries = metadata(spec["quantized"], spec["quantized_revision"])
            for name in ["onnx/model.onnx", "onnx/model.onnx_data"]:
                if name in entries:
                    fetch(spec["quantized"], spec["quantized_revision"], name, folder / name.split("/")[-1], entries[name])
        tokenizer = AutoTokenizer.from_pretrained(str(ASSETS / kind / "upstream"), use_fast=False, local_files_only=True)
        samples = TEXTS[:8] if kind == "embedding" else PAIRS
        outputs = []
        for path in [folder / "model.onnx", ASSETS / kind / ("model_quantized.onnx" if kind == "embedding" else "model_int8.onnx")]:
            session = open_session(path)
            values = []
            for sample in samples:
                args = [sample] if kind == "embedding" else list(sample)
                encoded = tokenizer(*args, truncation=True, max_length=128 if kind == "embedding" else 256, return_tensors="np")
                result = session.run(None, {i.name: encoded[i.name] for i in session.get_inputs()})[0]
                if kind == "embedding":
                    vector = result[0, 0] if result.ndim == 3 else result[0]
                    values.append(vector / np.linalg.norm(vector))
                else:
                    values.append(float(result.reshape(-1)[0]))
            outputs.append(np.array(values))
            del session
            gc.collect()
        fp, quant = outputs
        item = {"reference_revision": spec["quantized_revision"], "reference_sha256": digest(folder / "model.onnx"), "samples": len(samples)}
        if kind == "embedding":
            item["cosines"] = np.sum(fp * quant, axis=1).tolist()
            item["max_absolute_error"] = float(np.max(np.abs(fp - quant)))
            item["fp32_neighbor_order"] = np.argsort(-(fp @ fp.T), axis=1).tolist()
            item["int8_neighbor_order"] = np.argsort(-(quant @ quant.T), axis=1).tolist()
        else:
            item["fp32_logits"] = fp.tolist()
            item["int8_logits"] = quant.tolist()
            item["max_absolute_error"] = float(np.max(np.abs(fp - quant)))
            item["relevance_pairs_order_preserved"] = bool(fp[0] > fp[1] and quant[0] > quant[1] and fp[2] > fp[3] and quant[2] > quant[3])
        report["models"][kind] = item
        print(json.dumps({kind: item}, ensure_ascii=True), flush=True)
    output = ROOT / "artifacts/local-ai/quantization-smoke.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--offline", action="store_true")
    main(parser.parse_args().offline)
