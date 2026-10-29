# 模型未就绪时保留精确少看保护

## 发现的问题

SemanticOrder.rank原先只在候选向量和样本向量都存在时判断sample.key.equals(candidateKey)。模型准备失败、尚无向量时，连已知完全相同的少看样本也被当作中性内容；平台MORE随后可将其提回前排。另基础快照直接应用平台偏好，在省电/热限制使推理根本不启动时也缺少精确少看保护。

仅把精确匹配移出向量循环仍受模型负样本最多32条的窗口影响。最终Profile从有效反馈按既有同文本去重、显式优于弱反馈、后来显式选择优先的规则构建完整exactNegatives哈希集合，再另选有界模型样本。精确保护不依赖该32条窗口，画像签名包含完整精确负样本集合；SemanticOrder.VERSION升为examples-rrf-conflict-v7。

## 最终行为

- 对现有modelText（规范化标题及已有摘要）哈希完全匹配的有效负样本，无向量也进入已有内容负向分类档；不会用关键词推断相关主题。
- CandidateOrder.arrange的个人基础排序使用空向量规则及同一分类档的平台微调；CandidateOrder.forModels保持未经平台调整的候选基序。模型输入、模型数量和推理样本上限不变，最终平台偏好仍只应用一次。
- 有向量时仍按原正负相似度及精排保护处理；其他相关内容仍依赖模型。平台MORE、弱SAVED/READ不能覆盖明确LESS，撤销或后来明确MORE可解除对应保护。
- 这是软降权，内容仍在列表中；不代替BLOCK，不扩大成来源禁用或删除。按最新模式保持时间顺序，不应用该个人软排序。冻结可见列表和查看更新的交互边界不变。

## 证据

所有报告位于artifacts/local-ai，正常debug包/API36模拟器：

| 报告 | 结果 | 范围 |
|---|---|---|
| feedback-fallback-baseline-api36.txt | 5/3 | 实际SQLite反馈→LocalRecommendation准备模型失败路径，精确负样本/平台MORE及弱收藏/重开库三个保护失败；撤销和非语义标识通过 |
| feedback-fallback-fixed-api36.txt | 5/0 | 初次把精确判断移出向量依赖后通过 |
| feedback-fallback-verified-api36.txt | 5/0 | 加入每次prepare确实执行且仅一次的严格检查，不把热/电量提前暂停冒称准备失败 |
| feedback-base-snapshot-api36.txt | 7/0 | 加入模型启动前的基础快照保护 |
| feedback-full-exact-guard-api36.txt | 8/0 | 再加入40个实际SQLite负样本，让原负样本退出32模型样本窗口，重开库仍保留精确保护；模型准备失败路径仍严格检查 |
| semantic-full-exact-guard-api36.txt | 13/0 | 真实两模型、版本缓存/资源回归 |
| recommendation-recovery-feedback-fallback-api36.txt | 11/0 | 真实worker故障替换、取消及时限回归；该轮在补基础排序/全量精确集合前，LocalRecommendation执行恢复代码没有变化 |
| platform-feedback-base-snapshot-api36.txt | 15/0 | 实际来源菜单、迁移、偏好排序及撤销；该轮在补全量精确集合前 |

FeedbackFallbackProbe使用随机隔离库和明确合成候选，不改用户画像、实际模型目录或平台登录。模型prepare受控抛异常；不会通过删除/损坏用户模型制造失败，也不运行推理。完整集合版本新增单测验证旧负样本离开32窗口仍降权、后来MORE解除；另验证基础快照保留软降权、forModels基序和按最新不变。

build-feedback-full-exact-guard.txt成功，JVM341失败/错误/跳过0。精确保护不证明语义推荐命中率，不能据此宣称个人喜好已学会。后续完整公开候选回放及发现页结果另记录于本页末尾。

最终同生产包text-feedback-full-exact-guard-api36.txt为15/0：固定真实公开候选的开发者画像、LESS/撤销、冲突保护及另一100身份候选完成双模型。它不是用户标注或独立事件/时间留出。discovery-ui-full-exact-guard-api36.txt为77/0，原发现卡片、冻结更新、重建、缓存/交接及限制回归通过。diff检查通过，无本轮活跃构建/测试进程。

## 限制

规则依赖已存在的规范化标题/摘要完全一致，不会在模型不可用时猜测改写后的标题或相似事件。有效反馈集合按实际撤销/过期/重置处理；本文不声称用户全部历史条目被永久屏蔽。个人标注留出、长期推荐效果、真实来源全文讨论、真机内存功耗及精确release升级仍待完成。未提交、推送、发版或改品牌。
