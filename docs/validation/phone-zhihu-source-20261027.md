# 知乎真实来源与回答身份对照 — 2026-10-27

在整夜授权的Find X7 Ultra / PHY110 / Android16上验证普通f0候选。生产APK未改变，仍为f0d4dd5a82ac7510a2f7652f06707bdae10821333c3d7918abb2e44e98f01167。本轮仅调整测试驱动，未发布或推送。

## 来源与完整性断言

固定已采集的公开问题 https://www.zhihu.com/question/2098500549041547203 。真实LoginActivity共享WebView，等待来源普通“显示全部/阅读全文”按钮异步展开，不替换请求、页面或Cookie。日志仅保存路径匹配、界面profile证据、登录弹窗布尔值及内容长度，不输出账号、Cookie、回答作者或正文。

来源当前显示5条回答，profileEvidence=true，visibleLoginGate=false；补充去空白137字符，选定第一条可见文字回答1107字符。只在内存取该回答data-zop的公开itemId，经Java JSON读取保留长整数精度，避免JavaScript JSON数值转换改变ID。比较限定一个回答和补充各≤50000字符。

正常MainActivity启动不传discovery_candidate，不制造READ/QUICK_EXIT偏好；历史/缓存正常更新。先以来源回答开头40字精确选中唯一阅读节，实际展开，再检查data-reading-anchor包含同一公开回答ID编码。只比较克隆DOM中移除已知渲染控件后的正文，不修改实际阅读DOM；链接文字及正常业务段落保留。

## 测试视图选择缺陷

首轮zhihu-source-compare.txt为8项/1失败/code0：“未取得结构化回答”。旧辅助方法取视图树第一个WebView。生产SourceSurface.attach把加载来源的WebView插入content索引0，排版readerWeb位于其上方；来源常被保留供续读。旧探针因此可能一直检查来源DOM，而非用户看到的统一阅读DOM。

共享辅助代码改名ReleasePhoneSourceProbe，避免为每个平台复制一套访问/等待/菜单工具。获取来源视图仍允许普通WebView；读取正文时必须选择URL严格属于https://quiet-reader.invalid/且具有render查询参数的实际排版WebView，不按视图顺序猜测，也不反射混淆生产类。

修正后zhihu-reader-origin.txt显示VIEW_SELECTION firstIsReader=false / chosenReaderOrigin=true，证实该轮第一个WebView并不是排版页。生产SourceSurface代码另证实隐藏来源的插入顺序。这是测试选择器缺陷的证据，不是修改产品阅读视图或绕过来源限制。原首轮失败保留，但不能直接作为用户实际阅读失败的证据。

## 真实重新读取结果

zhihu-reader-origin.txt：产品“更多选项→重新读取”，14项/0失败，唯一终态code -1。阅读页补充去空白217字符，包含来源当前137字符全部文字；来源与阅读选定回答均1107字符，完整匹配，回答ID锚点一致，回答已展开、无横向溢出。217与137长度不同不等于来源之外的额外内容均已独立验证，本轮只断言来源显示的完整补充被包含。

允许已有缓存，产品重新读取请求已通过菜单触发，未独立保存传输计数/获取时间，不称传输层冷获取证明。UI profile并不证明某一具体登录受限内容权限，公开题也不能代替全部回答/权限/账号切换验收。只验证这个指定回答，未把第一屏5条当成全部答案。

同一最终驱动复核原虎扑入口：hupu-explicit-reader-origin.txt真实重新读取10项/0失败/code -1；firstIsReader=true、正文29字符完整匹配、67讨论节、前三条回复前缀3/3。共享工具改名/视图选择的Hupu路径运行通过。旧Hupu初次6/1来自同样有缺陷的选择器，缺乏当时视图或截图信息，不能继续把它定性为已复现产品首开故障，也不能追认具体根因已还原；后续实际用户异常仍需取正确排版视图及原生状态证据。

## 构建、数据和清理

证据 artifacts/local-ai/phone-night-20261027/：zhihu-source-build、zhihu-reader-origin-build均完整成功；最终驱动手机sha256sum与构建包匹配，普通App hash不变。临时安装通过ColorOS正常扫描，不关闭系统防护。中途UI检查仅保留固定错误词标志zhihu-running-flags.json，原XML已删除；此快照发生在探针结束后，不能用于证明失败时的界面状态。

结束临时驱动卸载Success、查询为空；删除指定手机XML和本测试主帖差异/截图路径，恢复stay_on_while_plugged_in=0，返回普通New并息屏Dozing。zhihu-summary.json从三份原始唯一终态、hash及清理文件生成。真实权限、贴吧逐条父子关系、正常Controller整App峰值/拔线耗电/长期缓存和真实个人推荐收益等门槛仍开放。
