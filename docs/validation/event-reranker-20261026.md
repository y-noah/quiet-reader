# 既有第二模型的事件复核实验

本轮是电脑离线推理与真实元数据回归，不改Android生产算法、不启动模拟器测试、不操作手机、不发版。

## 问题与原始证据

前一轮小模型余弦不能区分所有已标定相同/不同事件。本轮继续使用已经批准的`bge-reranker-base` INT8，分别以左标题为query/右标题为document及相反方向评分；不调用云模型、不增加第三模型。每对记录两个原始相关性logit及较小值，不把logit解释成事件相同概率。

新增`scripts/models/probe_event_reranker.py`。实际运行前，SentencePiece原生分词20个批准golden逐项一致，双序列/256 token截断5个golden逐项一致；5个数值oracle的最大绝对logit差为0。保存模型与SentencePiece文件SHA256。没有把fast tokenizer.json直接当成已对齐：独立检查发现两个控制/替换字符样本不符，因此实验使用与Android相同的原生SentencePiece路径。Android代码未变。

## 两批数据

1. 前轮已固定的285候选快照中13对：5同、5异、3不确定。
2. 另一份实际229候选旧快照中先固定10对开发者判断，再观察模型：2同、6异、2不确定。完整标题/URL/摘要/原始时间原样保留；判断及理由在`scripts/models/event-claim-stress-labels.json`，模型输出记录该文件SHA256。两批存在产品/人物家族交集，不称为完全独立留出，也不是本轮重新抓取。

第二批语料SHA256=02e840950fcb99c3c52893d756da441dfb1b3cf7c2e5572df3c17a8e184c5090，capturedAt=1792842386388。

## 实际结果

| 类型与例子 | 双向较小logit |
| --- | ---: |
| 同一婚礼致辞争议，措辞不同 | 6.5847 |
| 同一场BLG不敌MKOI，贴吧带评论前缀 | 7.1540左右 |
| 同一兵马俑女性指纹发现，名称不同 | 9.1408 |
| 成都蓉城0比1 / 中超第29轮0-1及评价提问 | 7.0254 |
| 男单/女单四强，不同项目 | 1.493左右 |
| 男子转院被丢弃 / 被丢弃致死，新增死亡结果 | 5.9553 |
| 结婚登记对数 / 初婚人数，不同口径 | -0.0212 |
| 不确定：“毛不易这情商” / “毛不易 情商” | 10.2909 |
| 不确定：“金正恩访问丹东” / “王沪宁陪同金正恩访问丹东” | 7.125左右 |

合计23对中，7对判同的最低值6.5847，11对判异的最高值5.9553；两者在这批观察中可分开。但5对不确定中最高10.2909，大于许多确认同事件。不能将“不确定”擅自改成“同”，不能只报已标定18对分开而隐去高分不确定样本。相关性模型会对缺失事件事实的同人物短标题给高分。

因此目前证据支持用第二模型辅助复核，不支持单独按分数合并。接入之前仍需具体事实/时间/进展保护、证据不足保持分开、来源入口/手动拆分/反馈兼容及资源预算。6.5等观察区间内数值不能当成已经在独立大样本验证的生产阈值。

桌面每批模型加载约0.23–0.37秒、总约0.89–1.20秒，仅是本机观测，不推算ARM、Android功耗/发热或App运行峰值。没有改变App推理次数/前台窗口。

原始结果在`artifacts/local-ai/event-similarity-20261026/observed-reranker-pairs.json`及`stress-reranker-pairs.json`。

## 生产边界回归

把第二批原始记录固化到仅JVM使用的`fixtures/observed-event-stress-pairs-20261026.json`。真实CandidateOrder结果：第一批同2/5、异误并0/5、3不确定排除；第二批同0/2、异误并0/6、2不确定排除。这些明确暴露剩余语义召回缺口，未用“测试通过”替代功能完成。

最终JVM518项、失败/错误/跳过均0。原XML及总数保存在`observed-both-snapshots-final.xml`和`observed-both-snapshots-jvm.json`。本轮只改开发工具/测试资源，没有新生产包或新真机验证；上一轮线程身份修复与所有全项目未完成门槛不变。

## 重放

开发隔离环境依赖`scripts/models/event-similarity-requirements.txt`，已有本地模型，执行：

```powershell
python scripts/models/probe_event_reranker.py --pairs artifacts/local-ai/event-similarity-20261026/observed-pairs.json --output artifacts/local-ai/event-similarity-20261026/observed-reranker-pairs.json
python scripts/models/probe_event_similarity.py --corpus artifacts/local-ai/text-hot-replay-20261024.json --labels scripts/models/event-claim-stress-labels.json --output artifacts/local-ai/event-similarity-20261026/stress-pairs.json
python scripts/models/probe_event_reranker.py --pairs artifacts/local-ai/event-similarity-20261026/stress-pairs.json --output artifacts/local-ai/event-similarity-20261026/stress-reranker-pairs.json
```
