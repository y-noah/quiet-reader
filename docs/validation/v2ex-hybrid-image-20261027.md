# V2EX 图片外围混合语法清理

公开来源 https://www.v2ex.com/t/1251768 的正文含 Markdown 空图片语法包住已渲染的链接图片：`![]( <a href="图片URL"><img src="同一图片URL"></a>)`。旧解析会在图片前后输出多余符号，原始公开片段保留在fixtures/v2ex-hybrid-image-fragment-20261027.html。

SourceParser仅在V2EX抽取副本中清理：有效HTTPS图片、直接图片或唯一图片链接、链接与图片URL一致、紧邻文本节点同时匹配完整空alt开闭语法。保留周围文字、链接及图片次序。代码块、非空alt、断裂/不匹配/非邻接语法不清理；其他平台不受影响。

三项新测试覆盖公开片段、正文与评论位置和保守边界。基线12项中2失败；修复后完整615 JVM/98XML均无失败错误跳过，Debug/测试包、Debug+Release Lint和签名隔离R8构建通过。指定模拟器discussion-flow实际72/0，验证讨论获取、缓存和续读回归；不是本片段实际画面验收。生产文件和当时APK哈希见artifacts/local-ai/v2ex-hybrid-image-20261027-identities.json，同前缀baseline、build、final、r8、discussion-flow保留结果。

这项改动在手机早间候选构建之后，未安装到手机，未推送或公开发布。真机测试期间按用户要求未继续修改生产代码；其后才开始首页导航/手势修改。
