# 真实标题对的事件归组基线

本轮仅电脑离线诊断，不操作手机、安装App、改生产分组或发版。

## 语料与人工判断范围

读取此前生产HotSourceCollector公开采集的`artifacts/local-ai/text-hot-context-current.json`，285条候选，capturedAt=1792867966176，SHA256=d84ae4f42e3c7d390db97999438ff985b5feee27340114d3251bcba99ba031a7。不是本轮重新抓取，也不是用户私人数据库或兴趣画像。

从原始记录精确选出13对标题，保留原URL、摘要、来源、获取及发布时间，开发者按事实主张判5对相同、5对不同、3对不确定。不改写标题来凑例子。标签不是用户兴趣，也不是独立专家复核。短标题不能证明的内容留为不确定，不算负例。

源码夹具`app/src/test/resources/fixtures/observed-event-pairs-20261026.json`保留证据及判定理由，仅供JVM测试，不加入生产资源。用户举例的县委书记事件没有在当前保存语料中找到，这一缺口仍存在。

## 现有生产分组实际结果

通过真正的HotCandidate.fromJson、CandidateOrder.arrange、EventGroups执行，5对相同合并2对，5对不同误合并0对；3对不确定排除。每一对均核对两个来源入口仍在。2对已合并均为标题相同；另外3对不同措辞的同事件仍未合并。不能将本轮JVM通过说成语义去重通过。

新增ObservedEventGroupingTest保护不同项目、不同进展、同产品不同问题等真实边界；它报告当前召回缺口，并未把漏合并改标为负例。最终512项JVM，失败/错误/跳过均0。保存原XML到`artifacts/local-ai/event-similarity-20261026/production-grouping-baseline.xml`。

## 已有BGE小模型实际推理

`scripts/models/probe_event_similarity.py`使用现有INT8 BGE小模型，在Windows CPU、2 intra/1 inter线程运行。只读已有模型、无网络/Key。20个既有分词golden样本逐一对齐后推理23个唯一输入，同时比较标题与标题+来源摘要。初次直接使用Rust tokenizer.json时两个包含控制/未分配字符的既有golden不符；补与批准BERT慢分词器一致的清理后20项全符。未改Android分词器。

| 原始标题主张 | 开发者判断 | 标题余弦 | 带摘要余弦 |
| --- | --- | ---: | ---: |
| 又输欧美,BLG爆冷不敌MKOI / BLG爆冷不敌MKOI | 同一比赛结果 | 0.7798 | 0.6569 |
| 王沪宁陪同朝鲜最高领导人金正恩访问丹东 / 王沪宁陪同金正恩访问丹东 | 相同人物、陪同访问动作 | 0.9070 | 0.9070 |
| 毛不易出席多年好友婚礼致辞引争议 / 毛不易婚礼发言引网友争议 | 同一次致辞争议 | 0.9067 | 0.9067 |
| 乒乓球亚锦赛男单四强 / 乒乓球亚锦赛女单四强出炉 | 不同项目 | 0.8590 | 0.8590 |
| 原婚礼致辞争议 / 新郎新娘回应婚礼发言 | 不同进展 | 0.8082 | 0.8082 |

同事件最小值0.7798，不同事件最大值0.8590。不存在一个标题余弦阈值能把这10对已判定样本全部分对；加入摘要也没有消除反例。分数不是合并概率。样本是挑选出的诊断对，不能推算总体精确率/召回率，更不能据此调出阈值并宣称在独立测试集通过。

推理结果、完整原始记录、20项分词计数、模型/外部权重/分词文件SHA256保存在`artifacts/local-ai/event-similarity-20261026/observed-pairs.json`。

## 后续实现约束

不能直接把当前兴趣向量相似度变成事件身份。需要保留可核对的事实区别：项目/人物/对象、数字事实、原消息与回应/辟谣/比赛结果；缺证据不强行归组。下一步候选归组须联合事实及时间证据，同时保护已有分开显示、屏蔽/看够了/撤销、锚点、冻结评价身份及非叠加热度。不能以新增第三个模型或每次浏览额外推理回避资源要求。

事件归组仍未完成，真实账号权限、本人推荐收益与整App手机资源验收也保持开放。手机上已安装的0193624E…包未改变。

## 重放

在短路径开发隔离Python环境安装`scripts/models/event-similarity-requirements.txt`，执行：

```powershell
python scripts/models/probe_event_similarity.py --corpus artifacts/local-ai/text-hot-context-current.json --output artifacts/local-ai/event-similarity-20261026/observed-pairs.json
```

原长工作区路径导致SymPy依赖安装触发Windows路径长度错误，已转工作区内短路径隔离环境；不修改系统Python。公开候选语料留在本地开发artifacts，脚本不会下载新语料或模型。实验时间/内存不能当成Android能耗或性能结果。
