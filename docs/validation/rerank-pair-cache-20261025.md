# 精排原始分数按实际输入对复用 / 2026-10-25

## 发现与边界

LocalRecommendation 原rerankKey包含 SemanticOrder.Profile.signature。该签名包含整份内容兴趣及平台偏好，因此仅调整平台偏好、或改变不影响某个实际query/候选文本对的其他反馈，也会丢掉已有原始模型分数。这与平台偏好独立计算及按需精排的目标相悖。

新增RerankReuseProbe使用实际BGE embedding和reranker、隔离SQLite及合成候选。不是仅构造假向量，也不是用模型名宣称省电。修改前 baseline（artifacts/local-ai/rerank-reuse-baseline-api36.txt）10项、3失败：

| 阶段 | 实际精排条数 | 提交前检查次数 | 向量 |
| --- | --- | --- | --- |
| 首轮 | 2 | 2（向量、精排） | 3/3 |
| 仅改变平台偏好 | 2 | 1（精排） | 3/3 |
| 改变另一项内容选择 | 2 | 1（精排） | 3/3 |

后两轮没有缺失向量，也没有改变原有两对精排输入，却再次提交了模型工作；SQLite还写入了不同画像键的重复分数。

## 实现

新增纯Java RerankCacheKey，以独立namespace、模型manifest revision、SemanticOrder规则版本、规范化query哈希、规范化候选实际模型文本哈希构造键。LocalRecommendation查写使用此键，不再把整份profile.signature或平台作为原始模型输入的缓存依赖。

缓存的是原始logit。每轮仍重新读取当前画像，计算向量正负证据、选择兴趣匹配、构造精排分组、应用当前置信档位和负反馈保护，最后融合平台偏好；仍使用当前来源/画像/generation检查提交最终快照。因此相同query/text的原始分数可以复用，不等于沿用旧个人顺序或旧负反馈惩罚。

模型revision、规则版本、实际query或候选输入改变则键改变。文本遵循现有1000码点SemanticOrder.input边界；实际兴趣query仍由Sample.query限制240码点，模型token上限及所有推理时间片保持原状。不会把平台名喂给模型，不新增模型、线程或后台工作。

旧画像键不猜测转换，不清除用户数据或文档向量；旧原始分数在新namespace下不命中，必要时首次重新计算。原5000行LRU边界仍约束新旧缓存，不增加容量或无界积累。本次没有执行跨APK升级验证。

## 验证

最终构建 build-rerank-reuse-final.txt 成功，JVM409，失败/错误0；新增3项RerankCacheKeyTest，覆盖模型/query/候选独立失效、拼接边界、规范化/Unicode/字面代码文本、现有截断边界与无模型版本拒绝。

API36两轮 rerank-reuse-rerank-reuse-final-api36.txt、rerank-reuse-repeat-api36.txt 均10/0：

| 阶段 | 实际精排条数 | 提交前检查次数 | 向量 |
| --- | --- | --- | --- |
| 首轮 | 2 | 2 | 3/3 |
| 平台偏好变化 | 0 | 0 | 3/3 |
| 另一内容选择变化 | 0 | 0 | 3/3 |

计数器接在LocalRecommendation的内存检查supplier，该入口仅在准备提交新模型batch时调用；0同时结合fullyComputed、semantic、全部向量命中和refined=0，证明这两轮没有进入新的模型提交路径，不能把它简写成整个App不占CPU。首轮实际执行两模型并在SQLite保存至少2对分数，后续行数不增加。微博平台偏好仍使微博优先；原明确少看仍落后；随后明确改为多看时重新构造画像，不残留旧少看理由。

相关回归（artifacts/local-ai/）：

- platform-preference-rerank-reuse-final-api36.txt：15/0。
- automatic-recommendation-rerank-reuse-final-api36.txt：8/0。
- recommendation-recovery-rerank-reuse-final-api36.txt：11/0。
- feedback-fallback-rerank-reuse-final-api36.txt：8/0。
- git diff --check及本次Java的行尾空白/冲突标记检查通过。

这是实际模型执行/缓存控制的模拟器证据，候选和选择为开发用合成数据，不是用户兴趣标签或个人P@20。只有当前所需向量/输入对已缓存才保证无需再次提交；新内容/新query/模型更新或缓存淘汰仍会计算。不据此给出省电百分比，也不证明Find X7 Ultra整体峰值或温升。个人推荐留出、真实账号全文/长期来源新鲜度、真机资源/耗电和精确升级仍待验收；未提交/推送/发版/改品牌，整体目标未完成。
