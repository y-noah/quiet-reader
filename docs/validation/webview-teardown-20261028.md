# 2026-10-28 WebView释放及原签名候选

## 触发与实现

真机来源/阅读/模型诊断中出现完整进程归属PSS合计1094411KiB（1068.76MiB），主进程161231KiB、网页渲染518045KiB、ranking415135KiB，超过1GiB。连续多次完整观测超过1GiB，不得将原预算判为通过。诊断包含直接服务推理，非生产Controller推荐效果证明。

检查发现LoginActivity.onDestroy以及MainActivity替换阅读WebView/onDestroy在视图仍有parent时直接destroy。统一复用原SourceSurface.release：stopLoading→从parent移除→destroy，并置空Activity引用；不清Cookie、缓存、localStorage，不终止共享渲染进程、不加轮询或新请求。此修复纠正生命周期顺序，不承诺它足以解决所有渲染进程内存。

## 实际修复前后

测试驱动仅用Android View.getParent及有界等待，无生产反射/注入。手机原33c9b4a2候选、同一新driver的phone-before.txt为6项1失败/raw0，来源关闭后仍有parent；保留原失败。修复8a5ca3f1的phone-after.txt为11/0/raw-1，来源WebView已断开parent，原真实虎扑正文/评论对照无回归；完整再跑虎扑11、知乎15、贴吧77、虎扑已读来源4、ARM双模型19均0失败/raw-1。仅关闭来源窗口的断言不能冒充每一种阅读替换路径均被独立观测。

## 构建、升级与审计

原签名候选New-20261028-8a5ca3f1.apk，SHA256 8a5ca3f199879587d3a3cdb05edfcd553224a44f04e1d02b2694da73b8a2d883。app.quietreader、47/0.4.0-upgrade-probe、target36，DEBUG=false/RANKING_MEASUREMENT_ENABLED=false，New品牌保持。模型5文件hash/大小、六64位ELF LOAD段16KiB、ZIP对齐、原证书ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37及全部ZIP成员已知明文凭据模式核验通过。

普通候选配置下848 JVM/118套，失败错误跳过0，lintDebug/lintRelease通过。保留userdata的API36普通AVD从0547经33c9到8a5，33c9升级前后/模型后保留检查12+12+12、模型最终19通过；8a5覆盖升级12、实际双模型19、再保留12通过，不清数据、不重种、不续Cookie。33c9首两轮模型检查被真实System UI isn't responding弹窗挡住，各4项1失败；保存XML/系统日志，按实际Wait控件恢复后同一APK19/0，不能删除失败或归因于New ANR。首次签名工具缺JAVA_HOME亦保留，补环境后签名检查通过。

实际手机/AVD App与driver散列均匹配归档；安全副本和current-candidate.json更新8a5，phoneInstalled=true/published=false/phoneValidationComplete=false。旧0547指针保存。归档artifacts/local-ai/webview-teardown-20261028含包、mapping/BuildConfig、完整构建/回归/Android/审计/安装散列/清理记录。两个AVD均关闭保留数据。

## 内存及仍开放项

修复后重复相同诊断阶段，保留真实缓存/账号/历史：63次150秒观测，19次归属完整，最大628852KiB（614.11MiB）；完整观测含ranking进程次数为0。其余未知不能记0，缺少同时含模型的完整观测，所以不能据此证明原1068.76MiB问题已解决，更不能认定整App峰值≤1GB。两轮时序、缓存和分配器状态不同，不作因果降幅结论。USB充电下26.7→26.6℃非拔线能耗结论。

下一步需针对真实Controller模型与网页的同时存活取得完整观测，区分直接诊断与实际推荐调度；按证据再治理资源。保留真实受限权限、完整分页、独立质量/用户收益、长期缓存及出站审计门槛。相关真机细节见phone-evening-20261028.md。
