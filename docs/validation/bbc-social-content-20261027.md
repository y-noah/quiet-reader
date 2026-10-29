# BBC公开文章社交块适配 — 2026-10-27

## 实际观察

artifacts/local-ai/public-source-access-20261027/。无Cookie主机公开请求：V2EX最热403/空响应；BBC热门阅读200/10项。Android生产Collector首次BBC超时0/1 READY，随后ArticleLive原生流程成功获取榜单第一篇，但正文content=false、unavailable=true。两次code-1仅表示诊断完成，不能称来源全部可读。

取得同一公开文章c6x2v4p284ylo/simp的HTML和NEXT_DATA，原文包含social和links两种此前未识别顶层块。原适配器未知块保护使整篇不可用。capture-manifest.json存公开文件hash和正文段落计数；testresources只存公开页面数据，生产assets未加入抓取结果。

## 修复与范围

只新增social：保留经过HTTPS/阅读链接校验的来源原帖链接，保留blockquote中的实质引用文字。用Jsoup转成纯文本，移除脚本、iframe、表单等，不执行远端嵌入、不调用平台API或拉视频；纯图片链接的占位文字不冒充引文。links为本次观察到的相关阅读卡片，与已有relatedContent一样不进入文章正文。未知正文类型仍使文章不可用，不能静默丢正文；原文章身份/语言/页码/来源校验不变。

新增实际文章回归先12项1失败（before-parser.log/xml）。修复后13项BBC定向通过，实际顶层文字块的每个paragraph含末尾均核对，原帖链接存在，相关阅读标题不混入正文，未知内容保护仍通过。新增恶意嵌入测试首先因默认折叠未渲染晚段引文而失败（first-after-parser.xml），纠正测试为先核对解析文档，再展开正文核对渲染，未放宽生产解析。全量673项0失败/错误/跳过，双Lint与Debug/测试构建成功，jvm-summary.json、after-parser.xml、after-build.log保存。

实际Android重读同一指定公开URL：browser=false/content=true/unavailable=false，47blocks/1section，正文cacheAccepted/cacheHit均true，视频位置/封面保留，无账号登录操作或Cookie值导出。android-bbc-article-after.txt唯一code-1/readable1/1/probeErrors0。独立抓取单篇，不是所有BBC文章、全榜最新性或全文权利证明；严格段落完整性来自同一公开冻结数据JVM回归，不从47块数量推断。

## 交付边界

本轮仅隔离Debug API36，无手机/推送/发布。停止指定测试包和AVD，保留userdata，cleanup.json记录退出进程。普通候选仍1a321e5d，未含本BBC源码；后续混淆/普通升级待做。BBC网络仍曾间歇超时，不盲目增加全局超时或后台重试。V2EX403未解决，不把它说成需登录或解析缺陷已经证实。真实账号/贴吧关系/ARM整App峰值/拔线能耗/长期预读/本人推荐收益仍开放。


## 隔离R8尝试：未通过，不更新普通候选

扩展已有ReleaseLiveReaderProbe，capture=social只选固定公开33段断言，不写正文fixture。refresh=true通过真实“更多选项→重新读取”触发产品刷新，避免误把Debug已读缓存说成R8解析成功；刷新使用产品本来的namespace失效，不直接清数据，作用仅限专用隔离包。独立capture偏好键不覆盖旧公开文章选择。保留原离线reuse模式，只有实际重新取得正文后才会运行；本轮未运行离线模式，未修改模拟器网络开关。

首轮构建因测试局部变量capture与catch变量同名失败，r8-build-first-failure.log保留；修正后R8构建成功。首轮运行因测试查找控件的String.contentEquals(null)崩溃，r8-reread-first-crash.txt和r8-crash.txt保留；改为null-safe字符串比较，只重建测试驱动，生产实现未改。

随后两轮真实重新读取均7项1失败/code0：菜单动作成功，但40秒内没有正文DOM（parts0/matched0/33）。r8-reread-source-timeout.txt和r8-reread.txt保留；首个失败截图r8-failure.png显示“需要来源页面协助／未能及时取得正文”。此前额外尝试抓同篇繁体页面也SSL握手超时。网络波动有迹象，但界面超时不能排除原生解析失败后fallback或R8特有问题，本轮不认定单一根因，不声称混淆兼容完成。

r8-summary.json明确r8_verified=false；r8-packages.json、mapping、BuildConfig与构建日志留存。没有降低断言、延长超时、注入正文或拿code0当成功。两次后停止重试，不做空离线验证；删除模拟器上一个指定临时失败截图、停止隔离包并关闭模拟器，保留userdata。手机未用，普通候选仍1a321e5d，不含BBC修复，未发版。下一步需把原生失败与来源超时进一步分清，或在稳定来源连接下复核R8；项目整体其他门槛不变。


## 后续：分离网络与R8解析，受控响应及离线通过

新增androidTest专用CapturedBbcTransport：仅discoveryprobe+ranchu可安装当前测试进程HTTPS URL handler，只对精确公开文章URL返回原捕获HTML字节，其他HTTPS请求拒绝。未改生产代码，不写Document缓存，不绕过生产Parser；不模拟TLS/来源可达性，不向生产assets加入fixture。原始HTML仅androidTest资产。实际产品菜单“重新读取”触发native Repository，从HTTP边界读取fixture；消费计数>0、缓存新时间及bbc-article身份防止把旧Debug正文冒充新解析。

隔离R8受控运行15/0/唯一code-1：公共阅读DOM33/33段、真实新磁盘正文33/33段、无横向溢出/自动播放器。随后独立instrumentation进程在系统无活动网络下16/0：同33段可读、采集时间及文件inode未变。两轮31项，见r8-captured-response.txt、r8-captured-offline.txt、controlled-summary.json。第一轮继承原通用“includes network acquisition”日志文字，但顶部明确TRANSPORT captured；离线驱动已改精确措辞，不据此声称在线成功。图片请求受控拒绝，跳过网络图片解码检查，不能说图片离线已缓存或成功显示。

原R8网络失败仍保留，r8-summary.json仅对应先前未通过的在线尝试；controlled-summary.json新证据把“同捕获网页的R8解析链路”确认通过，不能证明所有在线读取故障全由网络导致。最终驱动包摘要见controlled-r8-packages.json（新离线驱动另由构建产物生成）；拉取实际安装app hash与构建app一致，controlled-installed-hash.json。WiFi/mobileData原值1/1，finally恢复并逐值复核相同。移除模拟器两张指定临时截图，退出AVD保留userdata；普通1a321e5d尚未更新。下一步普通包升级；真机与真实网络仍需复核。


# 2026-10-27 当前普通候选与整夜手机检查

当前New-20261027-f0d4dd5a.apk包含BBC social/links修复，普通保数据升级/实际模型UI55项0失败，实际安装hash匹配；现已原位安装Find X7 Ultra并再次核对实际APK hash。手机固定公开BBC文章14项0失败/code-1，DOM及保存正文33/33段、首图解码800px；允许已有缓存，不称冷获取成功。自动阅读Intent不含个人反馈标记。临时驱动已卸载、stay-awake恢复0并息屏Dozing，未发布。真实账号/贴吧/整App ARM峰值/拔线能耗/长期缓存/本人收益仍开放。见 [普通候选](./ordinary-bbc-social-candidate-20261027.md) 与 [整夜手机证据](./phone-night-20261027.md)。
