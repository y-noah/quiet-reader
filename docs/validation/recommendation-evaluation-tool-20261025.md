# 独立留出推荐评估工具

日期：2026-10-25。对应主需求第9节的部分计算工具，尚无用户个人效果结论。

## 用途与边界

`scripts/models/evaluate_recommendations.py`是开发侧离线Python标准库工具，不随App运行、不访问网络、不调用模型、不产生API token、不读取手机数据库或Cookie，不把评估标签写回兴趣画像。本轮没有更改Android生产代码。

输入同一批事件候选的三个完整顺序：neutral中性顺序、embedding仅初筛、dual双模型。指标只消费明确标签，不推断用户兴趣。所有顺序必须包含相同完整候选集且不得重复事件；排序不同是唯一允许的列表差异。

这是固定学习画像、多个后续留出会话的协议：`training_events`为整组评估开始前冻结的学习事件。每个候选的观测时间必须在学习截止之后、排名冻结之前或同时；人工评价必须晚于排名冻结。学习事件不能出现在任一评估候选池。工具不会证明输入实际来自生产模型，也不能认证标签填写者身份，必须保留实际采集与模型版本证据。若中途用留出标签调整画像/参数，本组不再是独立留出，需要另建一组。

`event_key`为跨平台归并后的事件身份，不能直接把每个平台URL当成独立事件。相同事件多来源应在准备输入时合并，独立角度需人工判断并记录依据。工具能拒绝相同key重复/泄漏，不能自动识别两个不同key实际上是同一事件。这仍是实际评估的准备工作，不能据工具测试关闭M03或个人效果门槛。

## 输入协议

UTF-8 JSON，schema_version=1。只需事件身份、时间、顺序、选择，不需要正文、账号或登录凭据。下例两个候选是协议示意，不能作为用户标签或推荐效果证据。

```json
{
  "schema_version": 1,
  "label_origin": "synthetic",
  "training_events": [
    {"event_key": "learning-event", "labeled_at_ms": 90}
  ],
  "sessions": [{
    "session_id": "example-session",
    "learning_cutoff_ms": 100,
    "ranked_at_ms": 300,
    "candidates": [
      {"event_key": "event-a", "observed_at_ms": 200},
      {"event_key": "event-b", "observed_at_ms": 210}
    ],
    "rankings": {
      "neutral": ["event-a", "event-b"],
      "embedding": ["event-b", "event-a"],
      "dual": ["event-b", "event-a"]
    },
    "labels": [
      {"event_key": "event-a", "choice": "unsure", "labeled_at_ms": 400},
      {"event_key": "event-b", "choice": "want", "labeled_at_ms": 410}
    ]
  }]
}
```

- 时间单位为非负整数epoch毫秒，拒绝布尔值。示例小数字仅便于阅读，实际采集保留实际时间。
- `label_origin`允许user/developer/synthetic；只有确实由用户填写的标签才能声明user。开发者或模型标签不能改名充当用户选择。
- choice允许want/skip/unsure，缺失与unsure均为未知；重复标签拒绝，不能以覆盖方式悄悄改评价。
- 冻结顺序之后再收集评价；正式试验应盲化算法来源、随机化呈现并抽检非前排候选，当前工具不提供采集UI，也没有实现随机抽检或漏选指标。
- JSON重复对象key直接拒绝，避免同文件有两个label_origin或rankings却被解析器静默覆盖。

## 输出解释

每会话/模式报告实际前min(20,候选数)的位置数、want/skip/unknown及标签覆盖。仅在该模式前排全部有明确标签时输出precision；否则为null，不能把缺失项算成负例或悄悄缩小P@20分母。不足20的位置数如实报告，不能称完整20项试验。

汇总只包含三个模式的前排均完整标记的配对会话，并按实际位置数计算加权precision及双模型相对中性/初筛的百分点差。不能混入只有某模式已标记的会话。跨会话重复事件会报告，独立样本数量按事件key去重，不能靠反复评价同一事件凑100项。

`personal_sample_floor_met`仅表示自报user标签、至少5个完整配对会话、至少100个不同双模型前排事件、每模式至少100个完整标记位置。达到后才提供15/5个百分点目标的观察布尔值；它们不是稳定增益、置信度、整体验收或上线准确率声明。CPU/能耗/内存和双模型真实执行证据仍需单独关联，当前工具不生成这些数据。

## 运行与已验证证据

```text
python -m unittest discover -s scripts/models -p test_evaluate_recommendations.py -v
python scripts/models/evaluate_recommendations.py input.json --output report.json
```

输出不得与输入同一路径；不指定输出时打印报告。8项测试全部通过，覆盖已知计数/配对差、5会话100独立事件门槛与浮点边界、不确定/缺失标签、重复会话事件、短列表、时间/事件泄漏、排名集合不一致/重复、JSON重复key。

实际CLI链已运行，输入及输出分别位于artifacts/local-ai/evaluation-synthetic-{input,report}-20261025.json。该人为构造的5会话/200事件计算结果为30%/40%/45%，仅校验公式；label_origin=synthetic，因此personal_sample_floor_met=false、两个个人目标观察均null。这些数值不是App推荐效果，不能放入个人效果报告或作为15/5个百分点门槛通过证据。

仍待完成：从真实生产快照导出三种顺序及模型/画像版本，人工事件核对与用户标签采集、非前排漏选抽检、独立留出效果、真机资源和完整产品验收。本轮未提交、推送、发版、改名或更换logo。
