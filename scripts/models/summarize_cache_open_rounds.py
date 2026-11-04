"""Summarize measured live reader opens; slow product results are never test passes."""
import argparse
import csv
import json
import statistics
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--home-minute", action="store_true")
    parser.add_argument("--production-fix", action="store_true")
    args = parser.parse_args()
    root = args.directory
    all_opens, summaries, unavailable = [], [], []
    phases = ([("session.json", "每轮首页停留60秒")] if args.home_minute else
              [("rapid-session.json", "快速打开后返回"),
               ("dwell-session.json", "正文出现后停留5秒")])
    for filename, phase in phases:
        data = json.loads((root / filename).read_text(encoding="utf-8-sig"))
        if data["infrastructureFailures"] or len(data["rounds"]) != 3:
            raise ValueError(f"Incomplete measurement: {filename}")
        for board in data["boards"]:
            if not board["items"]:
                unavailable.append({"phase": phase, **board})
        for round_result in data["rounds"]:
            if args.home_minute:
                assert data["homeWaitMs"] == 60000 and data["readingDwellMs"] == 0
                assert round_result["actualHomeWaitMs"] >= 60000
            rows = [row for row in data["opens"] if row["round"] == round_result["round"]]
            assert len(rows) == round_result["opened"]
            assert len({row["index"] for row in rows}) == len(rows)
            for row in rows:
                assert row["instant"] == (0 <= row["readableMs"] <= 1000)
            slow = sum(not row["instant"] for row in rows)
            failed = sum(row["terminal"] != "readable" for row in rows)
            assert slow == round_result["notInstant"] and failed == round_result["failed"]
            times = [row["readableMs"] for row in rows if row["terminal"] == "readable"]
            summaries.append({"phase": phase, "round": round_result["round"],
                              "homeWaitMs": data.get("homeWaitMs", 0),
                              "actualHomeWaitMs": round_result.get("actualHomeWaitMs", 0),
                              "opened": len(rows), "notInstant": slow, "failed": failed,
                              "memoryHitsBefore": sum(row["memoryHitBeforeClick"] for row in rows),
                              "diskFilesBefore": sum(row["diskFileBeforeClick"] for row in rows),
                              "successfulMedianMs": statistics.median(times) if times else None,
                              "successfulMaxMs": max(times) if times else None})
            all_opens.extend({"phase": phase, **row} for row in rows)
    for name, rows in [("all-opens.csv", all_opens),
                       ("non-instant.csv", [row for row in all_opens if not row["instant"]])]:
        with (root / name).open("w", encoding="utf-8-sig", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=list(all_opens[0]))
            writer.writeheader()
            writer.writerows(rows)
    (root / "summary.json").write_text(json.dumps({"thresholdMs": 1000, "rounds": summaries,
        "unavailableBoards": unavailable, "totalOpens": len(all_opens)}, ensure_ascii=False, indent=2), encoding="utf-8")
    method = ("每轮首页初始化或刷新完成后保持前台至少60秒，再打乱当批顺序逐条打开；正文打开后立即返回，不额外停留。保留已有缓存、使用普通预读与模型，不清空数据。记录实际首页等待时长，三轮都必须达到60秒。"
              if args.home_minute else
              "每轮打乱当批顺序逐条打开，随后走实际刷新动作。第一组立即返回；第二组正文可读后停留5秒再返回。第二组保留已有缓存及历史，不是与第一组相同冷缓存条件，不能把差异全部归因于停留时间。")
    lines = ["# 真实来源缓存逐轮计时（2026-11-03）", "",
        "秒开定义：从触发实际首页打开逻辑起，到当前文章正文 DOM 就绪且 WebView 视觉回调完成，不超过 1000ms。未秒开数量包含超时/失败项，另列其中数量。",
        "", "环境：Android 16 x86 隔离模拟器、debug 构建、真实公开榜单与正文；没有注入正文或替换请求，按预定等待条件执行。不是 Find X7 Ultra 的真机速度。",
        "", method,
        "", "| 浏览方式 | 轮次 | 打开 | 未秒开 | 其中超时或未验证正文 | 点击前内存命中 | 成功项中位(ms) | 成功项最大(ms) |",
        "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for row in summaries:
        lines.append("| {phase} | {round} | {opened} | {notInstant} | {failed} | {memoryHitsBefore} | {successfulMedianMs} | {successfulMaxMs} |".format(**row))
    lines.extend(["", "说明：", "",
        "- 计时不把首页等待或阅读停留算入打开耗时；成功项耗时统计不包含失败项。每条等待正文上限为25秒，另有有限的DOM/视觉回调等待。timeout 仅表示限时内未验证到当前文章的文字正文，不武断归因为网络失败；纯图片正文不在此测量口径。",
        "- memoryHitBeforeClick 是点击前内存查询；diskFileBeforeClick 只确认文件存在，不额外读取文件暖缓存，不代表文件一定有效。bodyOrigin 和 sourceWorkObserved 是观察值，不替代精确 HTTP 请求计数。",
        "- 榜单没有返回内容的来源：" + ("、".join(sorted({row["source"] for row in unavailable})) or "无") + "。这些来源没有算入正文打开成功率。知乎/虎扑真实账号不在本组范围。",
        "- all-opens.csv 记录全部标题、URL、DOM/可读耗时、缓存线索和终态；non-instant.csv 只列未秒开项；session JSON 保留原始结果。",
        "- 探针基础设施完成不表示体验达标；未秒开和超时结果仍是待解决的问题。此次为修复后的本地验证，尚未发布新包。" if args.production_fix else "- 探针基础设施完成不表示体验达标；未秒开和超时结果仍是待解决的问题。此次没有修改生产代码或发布新包。", ""])
    (root / "cache-open-report.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(summaries, ensure_ascii=False))


if __name__ == "__main__":
    main()
