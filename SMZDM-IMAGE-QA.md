# 值得买固定商品缺图复核

## 0.3.11 原因取证

固定公开商品 `https://www.smzdm.com/p/183314225/`，不是后续榜首替代。原正式包 SHA256 `44CFB58C6608065B58A8407FD603134CBCB434129C25E33AA5C8E45D3246381D`。

1. Root 提供的真实 reader 失败截图已独立查看：文字原文存在、正文无图。证据 `artifacts/release-emulator-5556-20261002-143728-830/sources-1790923048822/source-6-first-image.png`，当时属于 **ABSENT**，不能称为已有图解码超时。
2. Session **92198 exit 0** 为窄来源诊断完成，不是正文验收通过。`artifacts/release-emulator-5556-20261002-144535-660/sources-1790923535652` 的两张来源图都已查看：同羊蝎子商品确有主图，250×250 自然尺寸，图片保持原色；来源桌面布局横向裁切，不冒充自有阅读器体验。
3. 实际动态 DOM：`IMG.main-img → A.img-box → DIV.info.J_info[articleid=3_183314225] → DIV#feed-main`，归属匹配，src 为 `https://qny.smzdm.com/202104/20/:image`，complete=true。不是推荐图或头像。
4. 模拟器内独立匿名 desktop-UA HTTP 请求得到 200，静态文本已有 main-img 标签 1 个、正确 articleid。它是独立请求且仅 token 计数，不证明与 earlier Repository 字节完全相同，但不支持「只能 JS 生成图」的猜测。
5. 解析规则当时只允许 zdmimg.com 图片家族，拒绝了这个实际 qny.smzdm.com 主图。属于真实漏图，不是正常无图文章被测试误报。

![原站商品主图确实存在](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-144535-660/sources-1790923535652/source-6-original-product-image.png)

诊断首轮记录缺陷：URL 形状函数未先排除 data:，把公开页面提醒二维码的 base64 纳入报告。已修为非 HTTP(S) 仅 protocol+[omitted]，候选缩小为 main-img/正文图，不取头像/二维码。两份主机自生成文本报告已去除此内容，原截图保留；已经输出的历史工具结果不可撤销，不再次转述。没有读取 Cookie、storage、账号或密码。新日志清洁需复验，不能以首轮脚本成功代替。

## 修复后验收计划

准确 0.3.12 正式包；从真实刷新列表找同 URL，公开搜索筛选后真实点击，检查 Main 主流程的同商品主图、自然尺寸、实际预览、关闭前任何 observer 移动之前的位置。若商品离榜，明确未覆盖，不注入假条目/替代图片。净化 probe 随后独立运行，保持用户数据。

## 0.3.12 修后实际结果

准确正式 SHA256 `8FF09200ABEDD8986DE9878BF61C88399F1E81EC20185884691DCB88486F8DA5`，保持全部数据。

1. **固定原商品真实主流程通过**：session **34553 exit 0**，目录 `artifacts/release-emulator-5556-20261002-145121-220/sources-1790923881211`，7 张截图全部查看。真实刷新 18 项中找到 183314225，公开搜索控件筛选后实际点进 Main 阅读；榜单 2.930 秒，正文至实际可读 2.610 秒。不是通过直接注入文档或更换榜首通过。
2. **主图确实回来了**：初始文档含同 qny/202104/20 主图，第一观察点 11ms 未解码、高 30 CSS px；第 2 观察点 573ms complete、自然 250×250、显示高 250 CSS px。自有阅读器截图显示同羊蝎子主图，颜色正常，非推荐/头像。
3. **实际大图和关闭通过**：真实 caption 点击后原生预览中该商品图可见（不是只有成功文案）；关闭后未被 observer 移动时 scrollY 与同图 top 差均 0px，原商品/自有文档身份保持。图片仍为源站提供的 250px 小图，不是高清提升，放大偏糊。
4. **净化诊断复跑通过**：session **25922 exit 0**，目录 `artifacts/release-emulator-5556-20261002-145309-629/sources-1790923989620`，两张来源图全部查看，仍是同商品和同主图。新日志仅一张 main-img 结构；全部文本中 `base64,` / `data:image` 命中 0。旧自生成主机报告已清理，模拟器同一报告也同步为脱敏版；未修改真实用户数据。这里的 PASS 是诊断完成，不是全站内容验收。

### 剩余可见风险

初始阅读截图只有低高度图片占位，图片稍后出现会将正文向下推约 **220 CSS px**。此次未在加载期定位正在阅读的文字，因此加载期阅读锚点稳定性仍未验证；关闭大图 delta=0 不覆盖此风险。573ms 是首图观察窗口内的确认时间，不是完整下载时长。来源桌面页面仍横向裁切，与自有正文体验必须区分。

![同原商品主图已显示](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-145121-220/sources-1790923881211/source-6-first-image.png)

![真实预览，仍为源站250px图](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-145121-220/sources-1790923881211/source-6-image-preview.png)

![关闭后的同文档原位置](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-145121-220/sources-1790923881211/source-6-preview-closed-unmoved.png)

本轮共修后 9 图独立审查，无账号认证宣称。测试源码、脚本与报告稳定，Gradle/ADB lease 已归还，无活动 session。下一项最相关验证是图片迟到加载时文字阅读定位，不应泛称已无阅读问题。
