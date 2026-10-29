# 预读组合负载与阅读器冷启动（2026-10-25）

## 发现与原始证据

在普通压缩App上启用实际前台预读，而非test_skip_fetch后，`active-prefetch-dual-20261025-a` 未取得正常终态：独立watcher明确报告DiscoveryActivity失去焦点时Input dispatching timed out；系统停止了instrumentation进程。遗留RUNNING状态不能算仍在运行或通过，主机collect按原规则拒绝。保留 `-interrupted-state.json / samples.ndjson / watcher.txt / operations.ndjson`、lastanr及进程logcat，未重用编号或改写为通过。

测试驱动增加每250ms的主线程心跳，滞后1500ms时最多16次采集线程堆栈到App专属stalls.ndjson；仅诊断测试包含，不进入普通App。`active-prefetch-dual-20261025-b` 再次以相同焦点事件超时退出，取得主线程在WebView首次构造中的Chromium初始化、WifiInfo类初始化、ConnectivityManager.getNetworkCapabilities Binder请求等堆栈，心跳滞后超过5秒。不能据此推断所有手机都会重现，也不能把该栈称为模型推理运行在主线程。

同时发现测试连接每次openDatabase缺少WAL标志，会尝试把生产FeedStore的wal模式切成TRUNCATE，并持续报告数据库锁定。修正测试连接为ENABLE_WRITE_AHEAD_LOGGING，避免观察者改变被测对象的日志模式。这个修正不是生产数据库修复，不据此宣布WebView卡顿根因已消失。

`active-prefetch-dual-20261025-c` 使用相同生产App103626FC…及WAL修正驱动BCBFE429D810921D533B1CCF15FAD01B854FCC1A7378F94148E6BFD556BCF537：7/0，总116128ms；首次点击之前已由普通preloader取得1条正文语言证据；实际101向量/6精排；100卡片/6篇正文，回放62105ms，独立身份/时间轴/结果检查通过。但是仍有最高4707ms心跳滞后，不能当冷阅读体验已经合格。

本场景正文是预先写入的固定合成磁盘缓存，验证生产preloader在点击前恢复、核对版本并形成正文证据，以及与模型整理在一个前台流程中交替；不是网络冷下载命中率证明。启动句柄、设备状态、操作identity已经绑定activePrefetch条件，分析器拒绝把开关不同的实验混比。主机watcher身份比较现允许am shell到app_process的合法exec转换，但仍核验PID、start ticks和完整instrumentation目标；11项Python检查通过。

## 最初产品改动（仅provider预热，后续已扩展）

增加Google官方稳定AndroidX WebKit1.18.0，使用接收WebViewOutcomeReceiver的稳定startUpWebView重载。没有使用旧WebViewStartUpCallback实验重载、反射或隐藏API。参考官方发布源码：

- https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.18.0/webkit-1.18.0-sources.jar
- https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.18.0/webkit-1.18.0.pom

AAR声明minCompileSdk33、AGP7.2，当前compileSdk36/AGP8.9.2满足要求。开启android.useAndroidX，依赖正常解析，没有手工替换系统WebView。

`ReaderStartup` 在发现页进入时每进程启动一次，通过官方接口把支持后台执行的初始化工作交给后台优先级线程，完成或失败后关闭专用executor；不持有WebView、Activity或文章，不预先打开来源网址。剩余必须在UI线程执行的任务仍由WebView提供者调度，不能承诺所有初始化都不阻塞。

启动期间暂缓自动来源启动、正文预读和推荐计算；回调后按既有前台/页面/政策条件恢复。列表本身仍可显示，已启动的网络工作不新增取消规则。初始化尚未完成时点击话题，合并成最后一次阅读请求并显示简短等待提示，完成后才导航；切页、失去窗口焦点、退出App或Activity销毁时不补开旧请求。初始化失败保留列表，通过原生提示阻止继续构造已失效的阅读器。

回调只弱引用Activity，退出后不启动模型或预读；没有定时重试、常驻WebView或推荐服务端。默认版本46/0.3.35和品牌均保留。

连续点击也加入导航防重入：已经发起阅读导航时忽略后续连点，发现页重新onResume后恢复；启动尚未完成时只保留最后一个请求，不排队打开多篇。onStop丢弃旧待开请求，页面切换/销毁后不补开。回放将每次卡片点击改为同一UI任务内连续3次，检查真实ActivityMonitor只收到一次MainActivity创建，并逐篇核对Intent内来源和URL确属当前固定快照位置，避免同标题跨平台条目被误认成同一内容。

## 验证与开放门槛

普通构建单元检查466/0；压缩构建和lint通过。最终隔离包的实际回放、冷启动心跳、后台/快速点击和原核心阅读回归结果待本轮补录。未把实现或构建通过当作性能结论。当前未连接手机、未推送或发版。

首个异步启动包D69DB93B8EF9397F0E0F83E158137C842A6878F64EE2EEDAA6C7D011DF925095、驱动28A8419D70560FC4418D81440C833DE0B1D6E349ADED382F6DACF127F297EA7B：`async-prefetch-dual-20261025-a`完整7/0，100卡片/6篇、普通主动预读标记与独立主机核验通过，未出现本轮原先焦点ANR。主线程监测仍记录7次长停顿，最大观察滞后3830ms，包括硬件渲染器、首次AwContents构造、网络状态Binder查询和loadUrl；没有达到“无长停顿”，不能据单轮4707→3830宣称可靠性能提升，更不能外推到真机。该精确包已归档，后续连点保护包需单独取证。

30分钟受控刷新/登录/媒体/网络回放、真正冷下载与图像预读覆盖、非充电真机整App峰值/耗电/发热、真实权限与个人质量评价仍保持原门槛，A08未关闭。

## 异步启动复现与实际视图预热

`async-prefetch-dual-20261025-b` 使用App664A74A8A9B6F3CF8B2CFB5BE627641502FE01CD048CE8F544B9B743A44A6D5D、驱动385642AFDBAE34BBC857DD73F86BFC1EB2EC00B11ED1CA0BC846D98611B7A640，再次被系统以FocusEvent失焦等待5008ms终止。保留五份interrupted原始记录，不能算通过。堆栈包含首次AwContents构造的ConnectivityManager/InputMethodManager Binder查询，以及首次loadUrl；前面的首页也观察到SharedPreferences/窗口渲染等待。因此仅官方异步provider启动不足以证明修复。

新方案在provider就绪后准备至多一个实际空白WebView：MutableContextWrapper只以Application为基础，无来源网址、JS/file/content访问，禁用网络请求并设置空白页CSP。空白页完成后才解除模型/预读门控；阅读页领取时将Context切到当前Activity并恢复普通统一阅读设置。未领取视图在配置改变、内存紧张或App无前台Activity持续一秒时销毁。已领取视图仍随原MainActivity销毁，不加入静态池。预热本身必须保留在主线程停顿检查范围，不能仅以点击后较快宣布性能达标。

`warmed-prefetch-dual-20261025-a` 有真实终态但5/1失败，首篇实际Intent/连续三点单实例检查通过，DOM回调超时；不存在100条完整通过。线程仍在首次阅读时构造AwContents，检查发现前一次测试启动已退出后台，在引擎初始化结束时没有前台Activity而跳过预热，此后进程once标志没有恢复准备。已补进入首页onResume时对“引擎已就绪、从未领取且空池”的重新准备，采用post而非在onResume内同步创建；不修改测试超时伪造通过。新包及连续冷进程复测待记录。

### 恢复逻辑修正后的同包验证

精确App SHA256 `46F8F71B2DA4EB67FA4E047E16F1762A8ADD4F0AFA8FA3B36E0A64DC19BA562D`，319414958字节；驱动 `385642AFDBAE34BBC857DD73F86BFC1EB2EC00B11ED1CA0BC846D98611B7A640`。两包与身份清单保存于 `artifacts/local-ai/reading-replay-20261025/archive-warmed-startup-bc-20261025/`。a失败旧包486F62A2…另存，未覆盖。

| 同包实验 | 真实终态 | 回放操作 | 主线程记录 | named进程采样 |
|---|---|---|---|---|
| warmed-prefetch-dual-20261025-b | COMPLETE，7/0 | 100条、6篇；62104ms完整操作标记 | 3条，最大滞后3376ms | 20/20有效，采样PSS最高553003KiB |
| warmed-prefetch-dual-20261025-c | COMPLETE，7/0 | 100条、6篇；62799ms完整操作标记 | 1条，滞后1597ms | 16/16有效，采样PSS最高526367KiB |
| warmed-reading-core-20261025-a | COMPLETE，35/0 | 原核心阅读、收藏、反馈、排序接纳及评价流程 | 原始stalls另存 | 12/12有效，采样PSS最高549194KiB |

两次回放来自不同冷进程、同一固定语料/画像，cold仍仅指向量/精排缓存清空，不是系统页缓存冷却。普通preloader点击前均产生1条正文语言证据；实际101向量/6精排。每篇验证当前来源+URL身份，连续三次点击只打开一个Activity，真实触摸展开、滚动、返回原列表锚点；主机操作分析器独立通过。正文依然是合成磁盘缓存，不证明真实来源网络加载/登录或个人质量。未复现先前ANR，但采样仍在首次loadUrl/页面导航路径观察到长停顿，不宣称卡顿已完全解决。

独立 `warm-view-lifecycle-20261025-a/result.json` 及四份原始meminfo记录：冷进程首页WebViews=1；HOME后三秒=0；返回首页重新准备=1；发送RUNNING_LOW后两秒=0，符合有限空白池释放预期。只证明模拟器这四个检查点的对象计数，不能当作堆泄漏检查或后台30分钟验收。该实验结束后force-stop隔离App，未操作手机原App。

最终源码普通JVM及构建成功，主机脱机分析器11项通过；未改驱动DOM观察超时，未提交、推送、改版号或发版。首次正文加载优化、长时间受控真实网络负载及真机门槛继续开放。
