# 退后台模型进程检查协议（2026-10-25，模拟器短测已完成）

本工具补充真实退后台与返回链，不能把前台30分钟回放或强制停止App作为后台安静证明。初始准备时30分钟前台回放使用已归档904BC7DE…驱动，以下源码当时暂不编译以避免改变活动测试；该前台长测现已完成，后续精确构建和后台结果见下。

测试专属energy-background复用固定100候选、明确NONE/EMBEDDING/DUAL模式、冷暖缓存及网络身份和真实模型准备链。随后注册目标Activity生命周期回调、通过UiAutomation执行系统HOME动作，要求5秒内观察onStop，不调用force-stop或重新绑定模型服务。10秒宽限后按2秒固定计划枚举同应用进程，要求主进程可见（列表未知不算空）、ranking不存在、未回到前台；离线配置同时检查无活动网络。逐条fsync落盘独立background.ndjson。

完整样本数量为durationSeconds*1000/2000+1，含起止检查点。身份、模式、画像语料、网络、明确onStop、10秒宽限、顺序、单调时间、完整结束标记与实际未出现ranking必须匹配。部分记录、列表缺失、重连、模型进程出现或意外返回前台均不能通过。最后正常返回原发现页，检查已显示快照与发现页可见性。

这是主进程存活条件下的离散模型进程缺席检查，不是完整CPU调度/wakeup trace、熄屏、后台所有线程静止或手机能耗证明；两秒之间短暂活动可能漏掉。测试驱动的心跳与采样本身也会唤醒，不能拿该负载证明整个App无周期唤醒。进入后台前的模型进程可能已经因正常空闲策略退出，因此不据此单轮声称活跃推理的取消延迟已验证。

主机检查18/0（原14项加4组后台完整性拒绝用例），新增Android源码尚未编译/部署。先等待当前前台30分钟回放终态与收集，再构建/短测该阶段。未操作手机，A08完整范围仍未通过。


## 设备环境摘要补充

脱离式收集器现在复用device_probe_metrics中的严格解析，汇总实际记录的电池/系统热状态检查点。未知、失败或截断不作零值，只有一个有效电池读数不生成电量变化；中间检查点出现充电时保留chargingObserved=true，即便两端是放电。温度是电池温度，热状态是系统枚举，均不是模型CPU温度或App独占能耗。单独主机34/0（18脱离工具+16资源解析/停止/环境用例）。正在运行的App及904BC7DE…驱动不受这些主机汇总或新增未编译后台源码影响。


## 拔线前台测试的屏幕超时保护（未编译）

发现既有回放没有独立的屏幕保持，模拟器USB环境不能证明手机拔线后不会按用户超时熄屏。新增测试专属、仅窗口级FLAG_KEEP_SCREEN_ON，覆盖准备/前台发现/正文，使用WeakHashMap保存原标志避免测试本身长期保留已销毁Activity。不改系统亮度、屏幕超时或电源设置，不持有wakelock；正常结束finally按原标志恢复并核对。退后台阶段发送HOME前先恢复所有窗口标志，避免把前台保持带入后台检查。异常进程退出后窗口销毁也不残留系统设置。仍需编译和运行验证，不将源码存在当作手机可拔线长测已通过。


## 后续构建/运行（旧“未编译”为当时记录）

原前台30分钟COMPLETE且已收集、恢复网络后构建成功。生产App仍D457C933…，最新驱动923103C4E5DF126BD2CADDEDFF3F0876E55C0792F572F5EA547AE036FB078B66，2827727bytes，归档archive-background-driver-20261025。background-dual-short-20261025-a已启动DUAL/cold/60秒、activePrefetch=true/uncontrolled网络，结果待补；不得将已编译或启动计为后台通过。


首轮background-dual-short-20261025-a COMPLETE5/1：模型两阶段与在线预读通过，但UiAutomation的GLOBAL_ACTION_HOME返回后5秒未观察到目标onStop，未进入后台采样。完整失败与空后台文件保留，不把该轮当成后台通过，也不能直接断定生产后台取消失败。主线程滞后记录主要在启动WebView阶段，尚无证据表明onStop本身阻塞。

改用UiAutomation授权的系统input keyevent KEYCODE_HOME，仍必须观察相同真实onStop和原5秒期限，增加所有停止/恢复回调及目标身份/焦点观测。生产App未改变；驱动0E02C2254E75E7620B492EE15AA1807551EB50E4DFD10C0591DEC75DC1168465，归档archive-background-key-driver-20261025，background-dual-short-20261025-b运行中，结果待补。未把动作接口返回成功当作实际生命周期证据。


b轮COMPLETE5/1，input命令输出为空，但5秒内仍无Application停止回调；focus=false、lifecycle=[]。该观察本身不能证明Activity已经处于后台，原始日志和空后台文件保留。尚未定位HOME送达/初始页面状态/观察器差异，不能把失败归因到生产退出模型。

c轮仍同App/DUAL/cold/联网预读工作负载，新增API29+目标Activity级生命周期监听与暂停/恢复/停止记录、送键前焦点/目标窗口/finished/destroyed状态，计时起点移至实际送键前，仍要求5秒内真实停止。驱动9FDCF8602FEF6EE9ACF78D601896C88540F60D8E95D89F2DB7ED406EBB6BF9F2，归档archive-background-observer-driver-20261025；运行background-dual-short-20261025-c，结果待补。未修改生产onStop实现。


## c轮完整后台短测

background-dual-short-20261025-c正式COMPLETE8/0。送键前focus=true/rootIsTarget=true/未finished未destroyed；实际目标停止观测3540ms，十秒宽限后完整60秒/31个2秒检查点均有主进程、无ranking、未回前台；随后原Activity恢复、已显示快照与发现页保持。主机backgroundQuiet身份/计数/时间轴/完整结束核验passed=true，28/28有效named资源样本。生产App仍D457C933…，驱动9FDCF860…；数据已收集。

本轮Home前rankingPids=[]，不能宣称活跃模型取消时延已测得。与a/b同时改了Activity级观察和送键前计时/状态记录，没有单独隔离Application观察为空的原因，不据此宣称修复了生产生命周期。保留a/b失败，c只证明其实际观察范围。

临时窗口保持在进入后台前及整轮finally的原标志恢复检查无异常；不等于真实手机拔线的屏幕行为已验证。同一新驱动replay-screen-none-20261025-a已启动NONE/online/activePrefetch/60秒前台100条6篇回归，结果待补。当前ADB只见模拟器，无物理手机连接。

## 前台屏幕保护回归与后台长测启动

replay-screen-none-20261025-a已正式COMPLETE7/0，主机functionalPassed/readingReplay.passed均true，固定100事件/6篇正文、在线普通预读点击前已有正文证据，实际展开/滚动/返回锚点通过。15/15有效named样本，最高203305KiB；NONE模式无模型缓存。生产App D457C933…/驱动9FDCF860…未改变。窗口原标志恢复路径未报错，但本轮仅60秒模拟器，不能替代真实手机拔线/息屏行为或30分钟前台能耗。首篇DOM观察3641ms、稳定控件3805ms，仍非帧呈现/真机首屏指标。

同包已启动background-dual-30m-20261025-a，DUAL/cold/1800秒、watchdog2100秒、online/activePrefetch，独立watcher9296且startTicks7471037已通过实际存活核对。当前仅RUNNING，不计通过；不在运行中重建/替换App或驱动。终态后收集独立后台日志和主机严格结果。手机仍未连接，已向用户询问下一轮可用时间。
## 完整后台30分钟结果

background-dual-30m-20261025-a已COMPLETE8/0，App D457C933…/驱动9FDCF860…，DUAL/cold/联网普通预读。实际onStop观测1638ms，停止后的10秒宽限后1800034ms、901个2秒检查点全部有主进程、无ranking、未回前台；正常返回原发现页与原快照通过。主机backgroundQuiet身份/计数/顺序/时间/完整结束passed=true。323/323有效named样本，最高473098KiB，非全App/真机峰值；能耗和连续无唤醒仍未验证。

此轮送HOME前确有ranking PID9811，首个宽限后检查点已消失，比前一短测入口即无ranking的证据更强，但进程存在不证明当时正在执行推理；没有精确取消时刻/连续CPU或wakeup trace，不将其写成活跃推理取消延迟合格。原始后台NDJSON和独立采样完整收集，结束后force-stop仅该终态模拟器隔离App。手机未受此长测操作影响。