# 澎湃文字热榜栏目分类

日期：2026-10-25。修复范围为明确来源栏目到界面分类的映射，不改变内容兴趣、平台偏好或全量候选排序。

## 真实缺口

ThepaperSource已把公开hotNews.nodeInfo.name保存在Item.sourceTag，Collector/HotCandidate/SQLite也保留；但ContentCategory.of只接受V2EX的node字段，澎湃明确的AI、影视、教育/公共事务栏目均成为OTHER。新增两个带公开固定样本的JVM检查，修复前427项中2项失败，日志artifacts/local-ai/thepaper-category-baseline-build-20261025.txt。

公开读取https://cache.thepaper.cn/contentapi/wwwIndex/rightSidebar，resultCode=1、20条，原始必要文章字段与nodeInfo保存在artifacts/local-ai/thepaper-public-tags-20261025.json。精简公开固定样本在app/src/test/resources/fixtures/thepaper-hot-nodes-20261025.json；其SHA256及来源记录在artifacts/local-ai/thepaper-category-evidence-20261025.json。未读取本机boards.xml、账号数据或Cookie。

## 映射边界

仅Source.THEPAPER下精确匹配来源栏目名：

- 科技：对齐Lab。公开栏目desc说明AI动向、技术。
- 文化：有戏。公开栏目说明影视剧综与审美生活。
- 社会：教育家、澎湃防务、浦江头条、一号专案、大国外交、中国政库、自贸区连线、全球速报。按平台明确的教育、公共/地区事务、法治、外交、政务及经济栏目归入当前宽泛社会分类，不代表对文章价值或政治立场作判断。
- 范围宽泛的澎湃评论、未纳入分类的运动家、未知/空字段保留其他；不按标题关键词或整个平台猜分类，不作字符串前缀/模糊匹配。跨来源同名字不借用此映射。

不扩大为所有澎湃文章都属于社会；分类不进入模型文本、不提供平台加分、无额外模型调用/联网/后台任务。既有缓存sourceTag即可动态过滤，无数据库迁移；历史无栏目项仍为其他。V2EX既有映射不改。

## 验证

- 固定公开样本20条，来源标记3条视频，剩余17条文字：社会12、科技1、文化1、其他3。测试真实SourceParser、Item→HotCandidate→JSON往返，不以手写分类结果冒充来源事实；来源精确匹配、宽泛/未知/跨来源不误用也有检查。
- 修复后JVM427/0、errors/skipped=0，完整debug/store诊断构建成功。日志artifacts/local-ai/thepaper-category-fixed-build-20261025.txt，XML汇总记录于evidence JSON。
- Android16/API36现有emulator-5560覆盖安装保留原签名诊断包。新增thepaper-category-ui模式，用同一公开固定样本通过生产解析和独立SQLite库加载DiscoveryActivity；标准无障碍ACTION_CLICK真实点击横向分类栏（先滚动使目标完整可见），检查选中、实际数量与类别、冻结列表相对顺序、ALL恢复、重开页面后文化筛选和sourceTag恢复、分类浏览不生成兴趣反馈。18/0，日志artifacts/local-ai/thepaper-category-ui-20261025-api36.txt。三个来源标记视频保持排除。
- 生产Collector/Repository实时公开热榜再次采集：READY、20条、20条有栏目、storageErrors=0；全体20候选分类TECH1/SOCIETY11/CULTURE1/OTHER7。日志artifacts/local-ai/thepaper-category-live-20261025-api36.txt。该数包含视频候选且实时榜单已变化，不等于固定样本的17条文字分布，也不证明每篇正文全部可读。

## 未完成项

这是一个来源已有分类证据被遗漏的修复，不是所有平台分类完整或准确率验收。知乎/微博等未提供可靠分类的候选仍不能仅凭平台和标题强行归类。真实跨平台分类覆盖、人工分类准确性、用户推荐效果、Find X7 Ultra资源/耗电及账号全文阅读继续保留原门槛。正式R8与普通升级APK尚未纳入本轮分类源码。未提交、推送、发版、改名或更换logo。
