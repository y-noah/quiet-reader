# 整夜授权手机检查 — 2026-10-27

用户授权连接整夜，可自行唤醒无密码手机，无需再次确认。Find X7 Ultra / PHY110 / Android16，普通app.quietreader保数据升级至f0d4dd5a；安装Success，拉取实际base.apk的SHA256与安全目录候选一致。未卸载、清除主App数据，未发布。原始证据 artifacts/local-ai/phone-night-20261027/。

## BBC公开文章

普通非debuggable R8包，临时同签名instrumentation驱动，live-reader / capture=social / phone=true / inspectIndex=0。严格限定普通包、PHY110和SDK36；没有受控HTTP替换。启动Intent省略discovery_candidate，不将自动阅读制造成个人READ/QUICK_EXIT反馈；正常阅读历史/缓存允许更新。

bbc-reader.txt唯一终态code -1，14项/0失败。固定公开URL https://www.bbc.com/zhongwen/articles/c6x2v4p284ylo/simp ，真实DOM及磁盘原生BBC身份正文均匹配全部33段（含尾段）。首图滚入视口后naturalWidth800，第二图也完成解码；无横向溢出或主动视频播放。

inspectIndex明确允许已有正文。日志中继承的“includes network acquisition”不能作为冷获取或加载时延证据，本轮不证明冷网络重新获取，也不推广为全部BBC文章成功。公开文章不能验证真实虎扑/知乎登录权限。

## 安装与清理

ColorOS安装测试驱动时，使用系统安装引导的深度扫描，显示暂无风险后继续安装；未关闭系统安全设置。测试后驱动app.quietreader.test卸载Success，包查询为空；手机临时new-night文件已清理。原stay_on_while_plugged_in为0，测试后恢复0，回到New首页后息屏，power-after记录Dozing。Dozing表示息屏/AOD，不代表App进程消失。verification-summary.json从原始终态、hash、驱动卸载、设置和电源结果生成。

充电状态下的测试不证明拔线耗电/温升。ARM实际双模型及WebView共同高负载峰值、真实账号权限/贴吧父子层与续页、个人真实标签收益和长期预读仍开放。

## 息屏静置资源观察

两份名册→内存→名册完整归属快照，相隔82.08秒，仅主进程535/startSeq12379，无ranking/WebView成员，服务查询均(nothing)。PSS从86489KiB降至67022KiB（约84.46→65.45MiB）。同一主进程生命周期CPU增加6tick，CLK_TCK100，即0.06CPU秒，平均单核约0.073%；采样本身可能增加开销。两次电源快照均Dozing。原始端点及计算见screen-off/。

这不是持续CPU/内存峰值采样，不证明中间没有进程启动，也不是清理最近任务后的观察。netstats/jobs命令未限定输出为本App，因此丢弃未限定的原始内容，不用来证明无网络/无任务。手机充电，不能得出拔线耗电结论。

## ARM 实际双模型与完整归属采样

同生产源码另外构建app.quietreader.flowprobe隔离R8包，未更改普通候选或用户画像。生产LocalModelEngine依旧一次仅加载一种模型；测试按embedding→reranker顺序真实请求，绕过个人结果缓存，不强行改变正常阅读停止推理的调度。隔离包首次安装前查询为空，模型文件首次不存在，冷准备与向量/JNI/有限logit/首页焦点/释放退出25项0失败、唯一code -1。

首轮约5.67秒、6个样本，完整归属4个；命名PSS最高704816KiB的那次名册发生变化，因此不能作为完整合计证明，完整归属最高仅334822KiB。为改善可观察性，测试驱动新增仅隔离foreground-models有效的modelRounds1..4与modelHoldMs0..2000参数，主机工具也校验范围；默认行为不变。每次模型输出后最多驻留2秒，低于服务8秒空闲退役时间；驻留是测试观察窗口，不代表持续推理耗时或用户交互延迟。

第二轮4轮/每模型后2000ms驻留，约22.41秒，72项0失败、唯一code -1。每轮两个embedding和两个reranker均真实返回；首页在每个ITEM输出时仍有焦点。20份内存采样中18份前后PID/name/UID/startSeq和精确packageList稳定完整，2份不完整；完整归属最高744994KiB（约727.53MiB），包括当时存在的主进程、ranking与所属隔离WebView。不能把不同进程各自高水位相加，不能声称连续瞬间峰值或全部场景≤1GiB已验收。

首轮及最终原始输出分别arm-foreground-*、arm-resident-*。模型准备约2435ms仅属隔离包首次准备，不是普通用户文章加载时延。最高观察电池温度26.5°C、系统热状态0；手机处于充电，不是拔线能耗比较，也不是长时间温升测试。短输入/短批直接服务请求不能替代正常Controller满候选排名、长文本或持续阅读压力。

构建包app SHA256 e1d4fe13c0ad41c7971bab41332b32e01658e4496b1ce07bec18a2679eb55009，最终驱动9a954f2ed25c22695cb89ad9d0c1ba133b7d6374514b560782aefd6bf0c8c983；手机实际两APK分别sha256sum匹配，mapping/BuildConfig归档。第二次构建只改驱动，生产APK hash不变。主机87项工具回归通过；最后补正live-reader的缓存许可日志措辞，原始旧日志保留，最终测试Java编译单独记录，不伪称该措辞已装到手机。

ColorOS隔离App扫描提示1项敏感权限，正常继续安装；驱动扫描后继续，未关闭防护或授予额外权限。与上一轮BBC驱动的“暂无风险”记录区分。结束force-stop并卸载flowprobe.test及flowprobe均Success，查询为空；删除new-night-arm.xml，stay-awake恢复0，返回普通New并息屏Dozing。arm-summary.json核对hash与清理原始结果。真实账号/贴吧、日常整体峰值/拔线耗电及真实个人收益仍待验证。

补充工具边界：隔离包BuildConfig为app.quietreader.flowprobe / version46 /0.3.35 /DEBUG=false /RANKING_MEASUREMENT_ENABLED=true；测量开关只在隔离包启用，普通f0仍为47/0.4.0-upgrade-probe且开关false。不能把隔离测量模式等同普通App所有路径。最终缓存日志措辞修正编译首次被受保护Gradle缓存拒绝（driver-final-compile.txt），保持原失败证据；使用已有缓存访问权限后driver-final-compile-access.txt成功，未再安装手机。3种无效采样参数在设备/文件访问前被拒绝，见sampling-argument-checks.json。

后续知乎/原虎扑入口真实对照与测试视图选择纠正见phone-zhihu-source-20261027.md及zhihu-summary.json；14/0和10/0为分项证据，不累计成全部平台验收。最终临时驱动已卸载、设置0及Dozing核对。


## 2026-10-28连续会话：贴吧

真实逐条楼层/楼内回复与一次自动续载完成，47项/0失败（格式化stream，未保留raw code）。7楼层、13唯一长子回复可比关系通过；5歧义、18窗口外不计通过。段数10→30，Home/onPause保存新鲜30段history。前几轮测试跨窗口短字串误配和错误Cookie文件名推导不能作为产品错误证据，详情与边界见 [贴吧专项](phone-tieba-source-20261028.md)。tieba-summary.json核对实际驱动hash与清理。驱动卸载、设置0恢复及Dozing均确认；普通App仍f0。整体目标与资源/权限/推荐收益门槛未关闭。


## 2026-10-28正常Controller与连续阅读资源

隔离R8固定100候选正常推荐/预读13/0，最终100视口/6次展开滚动返回15/0；中间两轮DOM callback失败11/1均保留。四轮最高完整归属样本737.45MiB，不是全场景连续峰值认证。普通App仍f0，实际hash核对；隔离App/驱动已卸载、原设置0及Dozing恢复，手机充电不作耗电结论。完整范围与观察修正见 [资源专项](phone-controller-resources-20261028.md)，原始arm-controller-*，汇总arm-controller-summary.json。真实网页/账号、全App峰值、拔线能耗及质量/长期缓存门槛继续开放。
