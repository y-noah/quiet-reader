# 实际文字供给、知乎上下文与真实长列表 / 2026-10-25

## 来源现场检查

API36模拟器使用正常生产 HotSourceCollector/Repository/DynamicBoard，在隔离数据库中请求默认九个文字来源。没有人工填入候选、没有启用视频平台、没有进行登录/验证码操作，也没有写入个人偏好标签。

artifacts/local-ai/live-text-supply-audit-round1-api36.txt 与 round2 两轮均 completed=true、ready=9/9、storageErrors=0，各取得300条来源候选。这里的300是来源条目，不能称为人工确认的独立事件。每轮使用实际生产请求，但这不是源站每个榜单都更新了内容、无CDN缓存或长期新鲜度证明。

后续 live-text-sources-context-supply-final-api36.txt 仍为9/9，来源条目285：知乎30、微博50、虎扑35、财联社20、V2EX38、IT之家12、澎湃20、百度50、贴吧30。默认 CandidateOrder 过滤/归组后282张卡片、282个来源条目、0个合并卡片。此数来自现有保守算法，不证明现实事件完全没有重复；不能用算法自身的分组数替代人工去重验收。数量随源返回、类型过滤与现场内容变化，不以之前300凑数。

## 发现并修正的元数据遗漏

实际公开知乎热榜 JSON 的 target 含 excerpt_area.text，本次30条中28条非空，首条873字。旧 SourceParser 只取标题/URL/热度，丢弃了这些来源已有的问题补充上下文。现保留为 Item.snippet，经过现有纯文本规范化并限制600个Unicode码点，供卡片和 HotCandidate.modelText 使用。

- 不发额外文章请求，不调用API模型，不扩大模型128/256 token输入上限，不把完整正文加入排序。
- 只接受 excerpt_area 对象下字符串类型的 text；缺失、null、数字、数组和对象保持空，不把结构对象转为摘要。
- 保留字面比较符、代码标签和实体文本，不将纯文本当HTML再解析，避免吞掉真实技术内容。
- 热度仍在 detail/rawHeat，不变成摘要/发布时间。摘要不是完整问题补充或回答，不将其写成已取得正文；完整阅读仍走原有会话和正文取得路径。
- 使用已有标题/摘要hash体系；下次实际榜单刷新时进入新元数据版本，不改来源身份、不迁移旧反馈或伪造旧摘要。

更新后的 live-text-sources-zhihu-context-final-api36.txt 及最终现场报告均显示知乎 sourceSnippets=28/30。拉取的公开 text-hot-context-current.json 共285条，知乎28/30条带来源上下文，最长UTF16长度600；Unicode边界另由JVM测试验证。公开导出只含HotCandidate事实，不导出Cookie或个人反馈。贴吧现有摘要30/30继续保留，其他来源无摘要时保持空。

## 真实候选到第100张卡片

新增 live-discovery-supply 诊断：实际启动默认生产 DiscoveryActivity 自动采集到隔离数据库，使用真实来源候选、默认过滤与归组，接受完整采集后的待更新快照，点击两次“查看更多话题”，定位第100张卡片并核对原始采集标题。没有以fixture或开发者标签替代现场内容。

初版现场测试 live-discovery-supply-context-supply-final-api36.txt 虽6/0，但actualCards=115，仅包含首批微博/知乎/虎扑；“数量已超过100”不足以证明已接收整轮来源。因此补上 visibleSourceVersion 至少达到采集结束时sources版本的等待，按新pending ID接收快照，防止仅停留在较早的分批结果。

强化后的两轮：

| 报告（artifacts/local-ai/） | 检查/失败 | 完整快照卡片/主要来源 |
| --- | --- | --- |
| live-discovery-complete-snapshot-api36.txt | 7/0 | 267，九个文字来源 |
| live-discovery-complete-snapshot-repeat-api36.txt | 7/0 | 282，九个文字来源 |

267张现场快照分布：微博50、知乎30、虎扑20、财联社20、V2EX38、IT之家12、澎湃17、百度50、贴吧30。复跑虎扑35，其余相同。保留动态差异，不把不同轮次当同一固定语料。所有主要条目满足默认中文/非视频资格、卡片身份不重复；这些自动资格检查不证明每篇正文均中文或每个事件都独立。

截图 live-discovery-text-page100.png 已拉取并查看：第100张实际贴吧卡片含摘要，第101张百度、第102张财联社正常排列，标题折行、菜单和底栏未见溢出或遮挡；部分下一张卡片在列表边缘露出符合滚动裁切。截图是本次明亮主题/默认宽度，不扩大为全字号/暗色/无障碍已验收，也不是知乎摘要的现场截图。

## 回归与边界

- build-live-discovery-complete-snapshot.txt 成功，JVM406，失败/错误0；新增 ZhihuHotContextTest 三项，涵盖真实字段到Item/候选/阅读元数据、纯文本保真及正文边界、异常字段、600码点/emoji和空白。
- discovery-ui-zhihu-context-final-api36.txt 77/0；automatic-recommendation-zhihu-context-final-api36.txt 8/0；discovery-prefetch-supply-zhihu-context-final-api36.txt 7/0。后续仅增强诊断，没有再改生产解析逻辑。
- git diff --check 及本次Java文件行尾空白/冲突标记检查通过。

现场供给和真实长列表取得了更强证据，但不证明推荐P@20、真实账号全文/评论分页、来源长期新鲜度或Find X7 Ultra整体RAM/耗电。V2EX在本模拟器网络取得列表，不代替用户手机VPN环境验证。仍需独立人工反馈/留出、真机资源与精确升级；完整目标未完成。未提交、推送、发版或改品牌。
