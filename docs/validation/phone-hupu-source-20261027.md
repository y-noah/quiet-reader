# 真机虎扑来源与统一阅读对照 — 2026-10-27

> 2026-10-28本地续查：既有匿名公开快照第一篇70行含67唯一pid，第二篇70行含63唯一pid；并非本页真机当时响应，不能追认逐条一致。另确认热门未渲染/空副本提前占ID的独立缺陷，已修复并进入本地2c622829候选，手机f0未更新。原评论数量和真实权限门槛不据此关闭，见 [续查、原失败及范围](hupu-reply-admission-20261028.md)。

> 后续测试选择器纠正：共享工具原先取第一个WebView，可能检查隐藏来源而非排版页。知乎同夜修正后实际观察firstIsReader=false，Hupu原入口用明确自身render页面再读10/0。下列首轮6/1保留为探针结果，缺乏当时视图信息，不能定性为确定产品首开故障，亦不追认唯一根因。详见phone-zhihu-source-20261027.md。

依据整夜授权使用Find X7 Ultra / PHY110 / Android16，普通f0候选不改生产代码、不清用户数据。新增仅测试APK的phone-hupu阶段，必须显式phone=true、普通包app.quietreader、PHY110和SDK36，帖子限两个已观察公开ID。实际本轮使用643330244.html。

## 对照方法与隐私

通过真实LoginActivity共享WebView打开帖子，等待#__NEXT_DATA__身份匹配、成功状态和真实主帖DOM。账号状态只在内存检查detail.user.puid是否非零，日志只保存signedIn布尔值；不读取导出Cookie，不输出账号ID/昵称或正文。正文/回复字符串仅在测试内存用于比较。随后正常MainActivity启动，不传discovery_candidate，因此不会制造READ/QUICK_EXIT个人反馈。普通历史/缓存照常更新。

比较结构化阅读正文是否包含来源当时完整主帖；若折叠，点击实际产品展开链接。检查讨论区存在及横向溢出，另记录来源前三条回复前80字匹配数。此匹配数是诊断值，不能当全部回复一致的断言。refresh=true使用真实“更多选项→重新读取”菜单，不直接改缓存/文档或调用混淆类。产品菜单触发的新获取请求已验证，未另外记录最终缓存acquisition时间或网络请求计数，不能把它描述为传输层冷获取认证。

## 真实结果与未解决项

证据目录artifacts/local-ai/phone-night-20261027/，hupu-summary.json从三份原始唯一终态生成。

- 首轮hupu-source-compare.txt：来源signedIn=true、restricted=false、主帖29字、已渲染回复70条。统一阅读45秒内未观察到结构化正文，6项/1失败/code0。首次驱动没有失败分类或截图，根因未定位，不能推断只因网络或Cookie，也不能宣称已修好。
- 增加只输出既知状态词命中布尔值及失败时New截图的诊断后，未改生产代码。第二轮hupu-source-diagnostic.txt：来源同状态，9项/0失败/code-1，正文完整匹配、68节/67讨论节，前三条回复前缀3/3，无溢出。由于成功，失败截图未产生；随后pull不存在图片返回错误，不当成截图证据。
- 再新增产品菜单重新读取模式，第三轮hupu-source-reread.txt：10项/0失败/code-1，同来源状态、正文匹配、68节/67讨论节、前三条3/3、无溢出。允许原有缓存的第二轮和请求重新读取的第三轮分别记录，不将二者混同。

这证明本轮来源实际识别为已登录且两个后续普通读取路径取得同一公开主帖及部分对应回复。帖子范围ALL_SEE，未验证LOGIN_SEE受限主帖/评论。来源70与阅读67的差额未逐条解释，因此不称完整评论权限等价、全分页或全部账号问题解决。首轮失败仍需更好的首次打开诊断与后续复现。

## 构建、清理与下一步

三次测试驱动构建日志hupu-driver-build、hupu-diagnostic-build、hupu-reread-build均成功；生产APK仍同hash f0d4dd5a82ac7510a2f7652f06707bdae10821333c3d7918abb2e44e98f01167。最终驱动手机sha256sum与构建包匹配，未重新安装主App。临时驱动首次通过ColorOS正常扫描/继续安装，更新直接完成，未关闭系统防护。

测试结束app.quietreader.test卸载Success/查询为空，删除指定new-night-hupu.xml与测试失败截图路径，恢复stay_on_while_plugged_in=0，返回普通New并息屏Dozing。无推送/发布。

后续须补来源可读而首开失败的分类证据、受限正文/评论真例、知乎登录及贴吧楼层/续页。短批ARM模型样本和息屏资源结果单独见phone-night-20261027.md，不能用于上述平台验收。

## 第二篇长文对照与测试修正

同夜使用另一白名单公开帖子643294354，普通f0生产代码未变。真实来源signedIn=true/restricted=false，原始main.innerText3480字符、去空白3253字符，渲染回复70条。第二篇首次正常取得结构化正文，没有复现第一篇“45秒内无结构化正文”。

首轮hupu-second-source.txt及诊断轮hupu-long-main.txt各7项/1失败/code0，失败为“完整主帖连续字符串出现在整页innerText”。诊断显示前后各80字都存在；旧探针只在本轮触发展开时才返回main字段，因此当阅读历史已展开时保存的readerMain为空，不能当产品正文为空。为定位，后者仅保存该白名单公开帖子主帖文字对照，未保存账号/页头/其他回复，与早先仅数字日志的范围区别。文件hupu-long-main-diff.json保留为此测试缺陷的证据，不称来源原始HTML。

ReaderHtml明确在图片后插入“查看大图”等渲染器控件，使整篇连续字符串断言不适合图文内容。修正探针始终克隆首个正文part，在克隆中仅移除产品生成的.section-label/.action/.section-nav/figcaption/style/script，读取textContent；不改实际页面，不删除业务正文块或放宽任意相似阈值。展开也只针对首个part，而非全页首个折叠回复。

最终hupu-prose-reread.txt真实产品重新读取10项/0失败/code-1，来源正文与阅读正文去空白都3253字，完整匹配。65个part/64讨论节，前三条回复前缀3/3，无横向溢出。来源70与阅读64差额仍未逐条解释，不能称全评论一致。此结果修正“疑似长文丢段”的测试判断，不消除第一篇初次读取失败；没有修改产品正文解析。

最终测试成功未写新差异文件，主机仍pull到了前轮同名文件。逐字节确认它与旧hupu-long-main-diff.json相同，删除本地重复hupu-prose-diff.json；不拿旧文件作为最终差异证据。测试源码随后增加启动前删除该固定临时诊断文件，避免未来混淆，单独最终编译记录hupu-prose-final-compile.txt，未再安装/运行此清理小改动。

本轮三个构建日志hupu-long-main-build、hupu-prose-build和此前驱动日志留存，主App hash仍f0；最终已装驱动hash与构建包匹配。结束临时驱动卸载Success/查询为空，删除指定手机XML/截图/主帖差异文件，恢复stay-awake0，回到普通New后Dozing。hupu-long-main-summary.json逐项核对原始失败/成功码、hash和清理。不发布。
