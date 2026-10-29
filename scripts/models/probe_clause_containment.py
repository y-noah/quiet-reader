"""Freeze a narrow event-admission challenge before scoring. No production changes.

One captured same-event pair plus deliberately synthetic nonasserting quotations.
The latter are uncertain rather than labeled different events: their full bodies
are unavailable. High title relevance must not authorize their event identity.
"""
import argparse
import hashlib
import json
from pathlib import Path


def build(raw):
    source = json.loads(raw.decode("utf-8-sig"))
    pair = next(p for p in source["pairs"] if p["left"]["title"] ==
                "西甲想当和事佬，皇马还在无所谓，傲慢的皇马还要装多久鸵鸟")
    anchor = json.loads(json.dumps(pair, ensure_ascii=False))
    anchor["id"] = "observed-editorial-clause"
    anchor["provenance"] = "captured public pair; existing developer-selected same label, not holdout"
    short = anchor["right"]["title"]
    pairs = [anchor]
    # All variants keep the exact entire short title as a final comma-delimited
    # clause. Several intentionally avoid current protected action/fact markers.
    for n, prefix in enumerate([
        "有人杜撰了这句话", "网上流传的虚构标题", "这句话只是举例",
        "这不是我的观点", "不赞成这种说法", "请别再照搬这个标题",
        "上个月的旧文章标题", "被冒用的文章标题", "原作者撤回了这个标题",
        "这是写作课上的反面例子", "我反对这种表述", "这句话断章取义",
    ]):
        left = dict(anchor["left"])
        left.update(title=prefix + "，" + short, snippet="",
                    url="https://example.org/synthetic-clause/" + str(n))
        pairs.append(dict(id="synthetic-nonasserting-" + str(n), label="uncertain",
                          reason="Exact quoted title alone cannot establish a current asserted event; no body evidence.",
                          provenance="synthetic adversarial title, not a platform observation",
                          left=left, right=dict(anchor["right"])))
    # Keep an already frozen different-event control so the common scorer's
    # two-class validation stays meaningful; no new invented negative label.
    control = next(p for p in source["pairs"] if p["label"] == "different")
    control = json.loads(json.dumps(control, ensure_ascii=False))
    control["provenance"] = "existing captured different-event control; not holdout"
    pairs.append(control)
    return dict(schema=1, corpus_sha256=source["corpus_sha256"],
                source_fixture_sha256=hashlib.sha256(raw).hexdigest(),
                scope="adversarial admission experiment; synthetic quotations explicitly marked; no personal labels, Android or holdout claim",
                candidate_rule="Only study final comma-delimited literal containment >=12 Han characters; all production admission guards and 7.0 bidirectional title threshold would remain. Not implemented.",
                rejection_rule="Any uncertain synthetic quote above 7.0 requires rejecting bare clause containment as sufficient added admission evidence.",
                pairs=pairs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = build(args.source.read_bytes())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as out:
        out.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(dict(pairs=len(result["pairs"]), sha256=hashlib.sha256(args.output.read_bytes()).hexdigest())))
