# 完整句段重合去重入口：反例实验（2026-10-28）

## 决策

拒绝仅凭“长标题以短标题完整句段结尾”新增事件复核入口。现有相关性模型即使双向都达到7.0，也不足以补上缺失的事实断言。生产规则、阈值、模型、候选指针及手机包均未改动。皇马评论长短标题这一已知漏合并仍然存在，不以补回一个正例为由放宽全部评论标题。

## 冻结与真实模型结果

`scripts/models/probe_clause_containment.py`从已捕获的新窗口抽取一个既有same标签对，另外构造12个非断言引用标题，并保留原窗口一个different控制。合成样本全部明确标记synthetic/uncertain；不是平台新观测、本人标签或独立留出。短标题为“傲慢的皇马还要装多久鸵鸟”。这些反例不声称属于不同事件，而是现有标题不足以建立当前事件身份。

先冻结输入，再运行现有`probe_event_reranker.py`。冻结SHA256为`f9e47f1cbb83dbd0d774c834bc3a016ae35541f3ad4d6bf3a3509d34c741897a`；拒绝准则在打分前写入输入：任一uncertain引用超过7.0，就拒绝裸句段重合方案。输出位于`artifacts/local-ai/clause-containment-20261028/scores.json`。

实际Windows CPU INT8、双向完整标题：

| 前缀／样本 | 双向较小logit |
| --- | ---: |
| 真实长短标题same对 | 7.470093 |
| 有人杜撰了这句话 | 7.500277 |
| 网上流传的虚构标题 | 8.863181 |
| 请别再照搬这个标题 | 7.110363 |
| 上个月的旧文章标题 | 9.127630 |
| 被冒用的文章标题 | 8.918102 |
| 原作者撤回了这个标题 | 7.764191 |
| 这句话断章取义 | 7.642930 |

表中合成项均在逗号后接完整短标题，精确结果以scores.json为准。12个uncertain中7个超过7.0；不同控制3.922344。不能因为same与different两类恰好分开，就忽略uncertain反例，或把这些数字解释为同事件概率。

SentencePiece单输入20项、成对5项golden一致，5个数值oracle最大差0；模型hash`2059d8ef0b6e935b4845e11b38c9af9e9e2e7b91f69fc99efe03254e0a7da8d3`。这是桌面实验，不是Android推理或推荐收益证据。首次误用不含numpy的recommendation-python未执行推理，改用已有quiet-reader-event-python环境成功；没有安装依赖或下载新模型。

## 回归及后续方向

`EventGroupAssemblyTest.completeQuotedHeadlineIsNotAnAssertedEventEvenWithHighRelevance`覆盖上述7个合成反例：实际当前计划不得新增推理任务；即使模拟收到99/99的旧任务结果，最终组装仍须保留两个入口及原ID。99是明确注入的测试分数，不冒充模型结果。用现有公开原文身份、明确声明或正文进展补足证据，才是后续处理此类漏项的方向；不增加每动作模型预算，也不立即把整篇文章交给模型。

普通候选参数下相关JVM三套47项（组装9、断言28、计划10）0失败/错误/跳过，XML逐套读取存入jvm-summary.json。首轮缺ANDROID_USER_HOME、次轮缓存JAR沙箱拒绝均未完成测试，原日志保留；设置正确路径并经原审批流程读取现有缓存后终态BUILD SUCCESSFUL。没有把两次构建失败隐藏成首次通过。生产未修改，不重建APK或重复全量Lint；现有候选实际SHA256仍8a5ca3f199879587d3a3cdb05edfcd553224a44f04e1d02b2694da73b8a2d883。

本轮手机ADB两次无设备，Windows仅识别手机蓝牙接口，未安装或操作手机。真实登录受限内容、整App ARM模型重叠峰值、拔线能耗等门槛继续开放。
