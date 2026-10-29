"""Freeze bounded metadata-context variants before running the existing scorer.

Developer-selected captured pairs are a tuning set. Synthetic adversarial
metadata is explicitly uncertain, never a new observed platform result.
No production input, admission, threshold, or inference budget changes.
"""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path


def render(candidate, limit):
    title = candidate["title"]
    snippet = re.sub(r"\s+", " ", candidate.get("snippet", "")).strip()
    return title if not snippet or limit == 0 else title + "\n" + snippet[:limit]


def build(raw, prior_scores=None):
    original = json.loads(raw.decode("utf-8-sig"))
    base = copy.deepcopy(original["pairs"])
    # Same subject matter and borrowed source excerpts must not establish that
    # a demand, rumor, conditional, or reversed position is the same assertion.
    apology = next(p for p in base if p["left"]["title"].startswith("如何看待皇马官方致歉"))
    for i, title in enumerate([
        "皇马尚未就姆巴佩言论道歉",
        "皇马否认已就姆巴佩言论道歉",
        "传闻皇马将就姆巴佩言论道歉",
        "如果皇马就姆巴佩言论道歉你会接受吗",
        "球迷要求皇马就姆巴佩言论道歉",
        "皇马去年就姆巴佩言论道歉",
        "姆巴佩本人就涉华言论道歉",
        "皇马官方致歉声明系伪造",
    ]):
        pair = copy.deepcopy(apology)
        pair.update(label="uncertain", reason="Synthetic title changes assertion identity; borrowed related excerpt cannot certify the current assertion.",
                    provenance="synthetic adversarial metadata, not an observed platform item")
        pair["left"].update(title=title, url="https://example.org/synthetic-context/" + str(i))
        base.append(pair)
    if prior_scores is not None:
        # Explicit follow-up challenge selected AFTER the first experiment:
        # institution statement was the sole additional same pair above7.
        # Never represent these controls as independent or pre-first-run data.
        statement = next(p for p in original["pairs"] if p["left"]["title"].startswith("央视体育青少中心："))
        for i, title in enumerate([
            "总台体育青少中心发言人：认可皇马的表态",
            "总台财经节目中心发言人：不认可皇马的表态",
            "总台体育青少中心发言人：尚未就皇马表态作出回应",
            "总台体育青少中心发言人：网传不认可皇马的表态不实",
            "总台体育青少中心发言人：是否认可皇马的表态",
            "总台体育青少中心发言人：去年不认可皇马的表态",
            "网友假冒总台体育青少中心：不认可皇马的表态",
            "总台体育青少中心回应：不认可皇马的表态系断章取义",
        ]):
            pair = copy.deepcopy(statement)
            pair.update(label="uncertain", reason="Synthetic changed speaker or stance; copied excerpt does not verify current statement identity.",
                        provenance="synthetic follow-up selected after first context scores; not holdout")
            pair["right"].update(title=title, url="https://example.org/synthetic-statement-context/" + str(i))
            base.append(pair)
    variants = []
    for i, pair in enumerate(base):
        for limit in (0, 96, 192):
            value = copy.deepcopy(pair)
            value.update(id=f"pair-{i}-context-{limit}", base_pair=i,
                         snippet_codepoint_limit=limit,
                         observed=i < len(original["pairs"]))
            for side in ("left", "right"):
                value[side]["original_title"] = pair[side]["title"]
                value[side]["title"] = render(pair[side], limit)
            variants.append(value)
    return dict(schema=1, corpus_sha256=original["corpus_sha256"],
                followup_after_scores_sha256=None if prior_scores is None else hashlib.sha256(prior_scores).hexdigest(),
                source_fixture_sha256=hashlib.sha256(raw).hexdigest(),
                scope="offline metadata representation experiment; original developer tuning labels and explicit synthetic uncertain controls; not holdout or Android evidence",
                candidate_rule="Replace each existing title input by title plus at most 96 or192 Unicode codepoints of its own existing metadata excerpt; same two model calls, no new fetch. Not implemented.",
                decision_rule="Do not treat >=7 relevance as assertion proof. Compare complete-input same misses and all different/uncertain controls; reject blanket context substitution if it increases unsupported high-score controls. Existing admission guards still require separate validation.",
                pairs=variants)


def summarize(raw):
    scored = json.loads(raw.decode("utf-8-sig"))
    result = {"scores_sha256": hashlib.sha256(raw).hexdigest(), "groups": []}
    for observed in (True, False):
        for limit in (0, 96, 192):
            pairs = [p for p in scored["pairs"] if p["observed"] == observed and p["snippet_codepoint_limit"] == limit]
            counts = {}
            for label in ("same", "different", "uncertain"):
                selected = [p for p in pairs if p["label"] == label]
                counts[label] = dict(total=len(selected), above7=sum(p["rerank_min"] >= 7 for p in selected),
                                     complete_above7=sum(p["rerank_min"] >= 7 and p["input_complete"] for p in selected),
                                     truncated=sum(not p["input_complete"] for p in selected))
            result["groups"].append(dict(observed=observed, snippet_codepoint_limit=limit, counts=counts))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--summarize", action="store_true")
    parser.add_argument("--followup-after-scores", type=Path)
    args = parser.parse_args()
    result = summarize(args.source.read_bytes()) if args.summarize else build(args.source.read_bytes(), None if args.followup_after_scores is None else args.followup_after_scores.read_bytes())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(dict(sha256=hashlib.sha256(args.output.read_bytes()).hexdigest(), pairs=len(result.get("pairs", []))), ensure_ascii=True))
