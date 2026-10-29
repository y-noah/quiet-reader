"""Offline exploration of observed hot-list pairs, never a production merge rule.

Developer event labels are not personal-interest labels. Embedding similarity
measures relatedness, not factual event identity or a merge probability.
No network, credentials, user database, or Android package changes.
"""
import argparse
import hashlib
import json
import os
import unicodedata
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"

# Exact titles select original records, including their actual dates and URLs.
# Uncertain pairs remain unscored rather than silently becoming negatives.
PAIRS = [
    ("same", "徐嘉余50米仰泳破世界纪录", "BAIDU", "徐嘉余50米仰泳破世界纪录", "WEIBO", "同一次同项目破纪录报道"),
    ("same", "新郎新娘回应毛不易婚礼发言", "BAIDU", "新郎新娘回应毛不易婚礼发言", "WEIBO", "相同的新人回应，区别于原致辞争议"),
    ("same", "又输欧美,BLG爆冷不敌MKOI", "TIEBA", "BLG爆冷不敌MKOI", "BAIDU", "同一对阵及结果，前缀是评论"),
    ("same", "王沪宁陪同朝鲜最高领导人金正恩访问丹东", "THEPAPER", "王沪宁陪同金正恩访问丹东", "WEIBO", "人物及陪同访问动作相同"),
    ("same", "毛不易出席多年好友婚礼致辞引争议", "BAIDU", "毛不易婚礼发言引网友争议", "WEIBO", "同一次致辞争议，无新人回应等新事实"),
    ("different", "乒乓球亚锦赛男单四强", "WEIBO", "乒乓球亚锦赛女单四强出炉", "BAIDU", "男女单为不同项目，即使文字高度相似"),
    ("different", "毛不易出席多年好友婚礼致辞引争议", "BAIDU", "新郎新娘回应毛不易婚礼发言", "WEIBO", "原事件与当事人回应是不同进展"),
    ("different", "国行 iPhone Duo 如何在境内写入境外 eSIM？", "V2EX", "iPhoneDuo破发", "WEIBO", "同产品的使用问题与价格事件不同"),
    ("different", "生死内战,TES火拼AL", "TIEBA", "TES战胜AL取首胜", "WEIBO", "赛前对阵与赛果应保留进展，不能丢掉新结果"),
    ("different", "鸡蛋别放冰箱门格子里", "WEIBO", "鸡蛋别水洗 生熟分开放", "BAIDU", "同主题不同建议，不是重复事实"),
    ("uncertain", "《尚公主》编剧水平被吐槽", "BAIDU", "尚公主编剧水平", "WEIBO", "短标题缺少争议动作，只有主题不足以确认"),
    ("uncertain", "金正恩访问丹东", "BAIDU", "王沪宁陪同金正恩访问丹东", "WEIBO", "访问与陪同是否同一进展需正文时间证据"),
    ("uncertain", "Kimi K3.1 Pro 发布, 比 K3 快、性能逼近 Opus，但额度 10 分钟就烧光了", "V2EX", "Kimi K3.1 Pro 正式发布：月之暗面最强 3 万亿级模型，编程智能体测评超 GPT-6", "ITHOME", "发布与额度体验可能应独立，保留人工待核"),
]


def observed_pairs(corpus, labels=PAIRS):
    index = {}
    for entry in corpus["candidates"]:
        key = entry["source"], entry["title"]
        if key in index:
            raise ValueError("Ambiguous observed title/source")
        index[key] = entry
    return [dict(label=label, left=index[source_a, title_a],
                 right=index[source_b, title_b], reason=reason)
            for label, title_a, source_a, title_b, source_b, reason in labels]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--labels", type=Path, help="Optional developer labels selected before model scores are observed")
    args = parser.parse_args()
    raw = args.corpus.read_bytes()
    corpus = json.loads(raw.decode("utf-8-sig"))
    labels = json.loads(args.labels.read_text(encoding="utf-8"))["pairs"] if args.labels else PAIRS
    if not labels or any(p[0] not in ("same", "different", "uncertain") for p in labels):
        raise ValueError("Missing or unknown event labels")
    pairs = observed_pairs(corpus, labels)  # Fail on missing evidence before model loading.
    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer
    from prepare_models import ASSETS, ROOT

    folder = ASSETS / "embedding"
    tokenizer = Tokenizer.from_file(str(folder / "upstream/tokenizer.json"))
    def encode(text):
        # Match BERT's approved slow-tokenizer clean_text for category-C and
        # replacement characters. Rust tokenizer.json alone retains some of them.
        clean = "".join(c for c in text if c not in ("\x00", "\ufffd")
                        and (c in "\t\n\r" or not unicodedata.category(c).startswith("C")))
        return tokenizer.encode(clean)
    golden = json.loads((ROOT / "app/src/androidTest/assets/local-ai-golden.json").read_text(encoding="utf-8"))
    parity = 0
    for example in golden["tokenizers"]:
        if example["kind"] == "embedding":
            if encode(example["text"]).ids != example["ids"]:
                raise ValueError("Tokenizer does not match approved parity fixture")
            parity += 1
    if not parity:
        raise ValueError("No tokenizer parity evidence")
    tokenizer.enable_truncation(max_length=128)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.enable_cpu_mem_arena = False
    options.enable_mem_pattern = False
    options.add_session_config_entry("session.disable_prepacking", "1")
    session = ort.InferenceSession(str(folder / "model_quantized.onnx"), options, providers=["CPUExecutionProvider"])
    vectors = {}
    def vector_for(text):
        if text not in vectors:
            tokens = encode(text)
            encoded = {"input_ids": np.array([tokens.ids], dtype=np.int64),
                       "attention_mask": np.array([tokens.attention_mask], dtype=np.int64),
                       "token_type_ids": np.array([tokens.type_ids], dtype=np.int64)}
            output = session.run(None, {i.name: encoded[i.name] for i in session.get_inputs()})[0]
            vector = output[0, 0] if output.ndim == 3 else output[0]
            norm = np.linalg.norm(vector)
            if vector.shape != (512,) or not np.all(np.isfinite(vector)) or norm <= 0:
                raise ValueError("Invalid model vector")
            vectors[text] = vector / norm
        return vectors[text]

    for pair in pairs:
        title_vectors = [vector_for(pair[side]["title"]) for side in ("left", "right")]
        pair["cosine"] = float(np.dot(*title_vectors))
        model_vectors = []
        for side in ("left", "right"):
            entry = pair[side]
            text = entry["title"]
            if entry.get("snippet") and entry["snippet"] != text:
                text += " " + entry["snippet"]
            model_vectors.append(vector_for(text))
        pair["cosine_with_source_snippet"] = float(np.dot(*model_vectors))
    same = [p["cosine"] for p in pairs if p["label"] == "same"]
    different = [p["cosine"] for p in pairs if p["label"] == "different"]
    result = dict(schema=1, corpus_sha256=hashlib.sha256(raw).hexdigest(),
                  captured_at=corpus["capturedAt"], candidate_count=len(corpus["candidates"]),
                  label_file_sha256=hashlib.sha256(args.labels.read_bytes()).hexdigest() if args.labels else None,
                  scope="Windows desktop title-only vs title/source-snippet INT8 embedding exploration; not Android acceptance or personal-interest evaluation",
                  labels="developer-reviewed claims from observed titles; uncertain pairs excluded from threshold bounds",
                  tokenizer_parity_checks=parity,
                  model_sha256=hashlib.sha256((folder / "model_quantized.onnx").read_bytes()).hexdigest(),
                  model_data_sha256=hashlib.sha256((folder / "model_quantized.onnx_data").read_bytes()).hexdigest(),
                  tokenizer_sha256=hashlib.sha256((folder / "upstream/tokenizer.json").read_bytes()).hexdigest(),
                  min_same_cosine=min(same), max_different_cosine=max(different),
                  separates_labeled_pairs=min(same) > max(different),
                  encoded_unique_inputs=len(vectors),
                  production_rule_changed=False, pairs=pairs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "pairs"}, ensure_ascii=True))


if __name__ == "__main__":
    main()
