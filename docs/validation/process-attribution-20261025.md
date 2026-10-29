# 设备采样中的进程归属核对（2026-10-25）

本轮为资源验收工具补齐，不是整个App峰值或能耗验收完成。生产代码及APK未变化，采集仅存在于独立测试APK。未操作用户手机、提交、推送或发布。

## 实际发现与处理

API36模拟器系统的`dumpsys activity processes app.quietreader.flowprobe`提供独立WebView renderer的`packageList={app.quietreader.flowprobe}`。本轮基线主进程UID10222、WebView UID99623，名称及UID不同但系统明确关联同一包。`dumpsys meminfo --package`在本轮确实也返回这两个进程，不能编造成已发现它一定漏算；过去只有MEMINFO标题/PSS，缺少独立归属和进程稳定性核对。

DetachedProbeRecorder每轮在PSS前后各记录一次系统进程列表，并记录采样结束时间。主机解析器只采用`All known processes`到`OOM levels`之间的完整APP记录，按packageList精确成员归属；不依赖WebView进程名前缀或同UID，不把后文服务引用/PID映射当作归属。PID、进程名、UID、startSeq前后一致，PSS记录无缺失/额外/重复且非零，才产生本轮attributed合计。birth/exit、PID复用、格式未知、命令失败、截断与缺失数据保持未知，不补零。startSeq仅在同一system-server生命周期内解释。

不能因此宣称完整硬件归属或连续峰值：两次系统列表与各进程PSS仍为顺序查询；查询之间短命进程、系统/GPU共享服务、采样间高点未获证明。wholeAppPeakVerified与energyComparisonVerified保持false。普通App运行不增加采样或新权限。

## 代码与边界检查

- scripts/models/device_probe_metrics.py：新增parse_package_processes/attributed_pss。
- scripts/models/detached_device_probe.py：汇总schema2，保留既有named结果，单独输出completeAttributedSamples、sampledAttributedPssPeakKiB、attributedEvidenceAvailable。旧日志没有新字段时仍可读，归属完整样本为0。丢失原始记录时不授权attributedEvidenceAvailable。
- app/src/androidTest/java/app/quietreader/DetachedProbeRecorder.java：加入前后系统列表与finishedElapsedMs。仍沿用512KiB单命令上限、退出码/截断/耗时原始记录、8秒超时控制和通知停止逻辑。

新边界单测10/0，既有detached18/0、诊断metrics16/0。覆盖独立renderer与相似包名、共享packageList、多余引用、缺失renderer、生命周期改变、失败截断、重复PID/PSS、缺主进程及旧日志兼容。解析器亦现场读取真实系统基线成功；这不是ColorOS全部版本格式兼容证明。

## 最新精确包在线回放

证据目录artifacts/local-ai/process-attribution-20261025。生产App89C64F439843478C46E86A3DE6BF7769C429737AAE18D4001D0490F16506B875（与来源恢复修复版相同），新驱动3C2251ED12D73C44A20ADC73448EE321C256A932C2709334D121A6654277197B。驱动与元数据归档driver/，生产APK沿用new-source-restore-release-20261025归档。

attribution-online-20261025-a：COMPLETE7/0、153549ms；严格readingReplay通过100个独立事件及6次卡片打开、展开、滚动、返回。DUAL实际101向量/6精排对，首个读页点击前preparedEvidence=1。回放区间62333ms、activePrefetch=true、network=uncontrolled。正文是合成磁盘缓存，不是100篇真实网络下载、真实登录或本人兴趣效果证明。

21条原始采样记录与终态计数一致。named完整20条，归属/生命周期完整19条，其中16条含独立WebView、8条含:ranking。两个归属不完整样本分别发生于106330/119351ms，前后进程集合改变；保留未知，不将较小数字计为完整占用。有效归属采样最大476150KiB。这只是模拟器本次工作负载的观测，不是手机峰值≤1GB结论。

采样总耗时中位2052ms、最大7414ms，实际轮间另有配置5秒延迟，不能称为5秒固定频率。系统列表前/后中位229/154ms，meminfo中位1004ms，另有proc/电池/热/磁盘查询。该开销不能当作普通AppCPU或能耗；需要无采样对照判断测试对阅读性能的影响。

本轮首篇Activity/正文/稳定控件观测8777/10406/10567ms，明显慢，不能因为功能7/0便宣称打开速度已达标。其余五次观测与全部原始日志保留。这是instrumentation观察，未测真实帧呈现，未控制设备负载、页缓存或测试顺序，不能从一次对照推导普遍速度/能耗改善。

## 新采样查询的停止边界

attribution-stop-20261025-a：独立control-idle故意8秒截止，设备终态TIMED_OUT、8013ms，3条采样；同一watcher已退出，随后pidof主进程/:ranking无结果。已收集终态/独立输出/原始数据，不算成功功能回归，也不把故意停止后的空进程当模型内存。新增系统查询未使本轮超时清理悬挂；不证明所有设备/错误路径。

## 当前后续项

同一App/驱动的无采样对照attribution-nosampler-20261025-a已终态7/0；操作日志经同一严格host校验通过100/6，回放区间62853ms、101向量/8精排输入对。未启用DetachedProbeRecorder，保留独立watcher输出、原始操作日志与summary.json。

首篇Activity/正文/稳定控件观测3091/4969/5141ms，比带采样这一轮低，但仍有数秒等待。后续五篇正文观测1808/1266/2176/1372/1384ms。该单次顺序对照存在OS缓存、实际精排工作量、调度与测试先后差异，不据此给采集开销或产品提速下因果结论，也不把采样移除说成生产修复。首篇真实体验仍需进一步分段诊断/真机帧观察；没有将功能通过改写为“缓存秒开”。

所有已启动句柄都已终态并保存证据，最后对隔离flow应用force-stop。尚需真机全进程检查、长负载与无USB充电的受控能耗/发热对照、采样开销评估及真实账号阅读。A08保持开放。
