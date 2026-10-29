# 刷新动画与排队取消：普通原签名候选

本轮将刷新动画和过期推荐输入的提前取消打进普通原签名压缩包。New品牌不变，未发布、未访问手机。当前本地候选 `New-20261028-e84c1cb3.apk`，SHA256 `e84c1cb31f001b13b53c9f5936effc05c6789edf71b8d8f9baf0b0987caa8e47`；手机仍是8a5ca3f1。安全副本及current-candidate.json已更新，phoneInstalled=false，保留lastPhoneCandidate。

## 构建与普通升级

`build-release-probe.ps1 -Offline -NormalUpgrade`成功，R8实际执行；后续仅测试驱动变化，重复核对生产APK哈希不变。包名app.quietreader、版本47/0.4.0-upgrade-probe、target36，DEBUG=false和RANKING_MEASUREMENT_ENABLED=false。归档包含mapping和BuildConfig。

保留quiet-reader-upgrade-api36的既有userdata，不重种合成升级数据、不续Cookie、不清缓存。原8a5普通包12/0，新包12/0，实际双模型19/0，刷新两轮后再保留检查12/0，均唯一raw-1。模型检查覆盖原生入口、模型校验、512维归一化向量、精排有限logit及SentencePiece JNI、解绑后worker退出；不是生产Controller个人推荐收益或手机能耗验证。

最初误用隔离包upgrade-verify入口，两次0检查/1失败/raw0，被仅允许installprobe的保护拒绝。保留upgrade-before/after原日志；随后重新安装原8a5，再用normal-upgrade-verify跑完整前后流程，不以失败入口证明升级。

## 压缩包刷新验证

新增ReleaseRefreshAnimationProbe，仅允许带既有合成升级标记的普通API36模拟器；只使用Android原生View、Drawable、Bitmap和点击，不反射生产类、不改数据库/偏好/来源。最终连续两轮11/0、各唯一raw-1：非debug目标、既有数据、空闲圆点、前台焦点、48dp点击区、真实点击、主线程排队返回前立即空心圆环、无障碍忙碌状态、连点期间圆环及点击区保留、真实结束恢复实心圆点。此项不单独证明连点只发一次请求；请求合并和角度推进等此前Debug专项证据见refresh-animation-20261028.md。

保留真实测试失败：首轮11项1失败，结束判据只接受contentDescription=刷新，误拒绝“查看更新”。诊断复跑因启动异步未就绪时过早点击，测试主线程断言直接抛出导致进程崩溃；crash.txt栈明确来自ReleaseRefreshAnimationProbe。只改测试驱动捕获主线程断言、等待启动调度，不改生产异常处理。后续同判据复跑记录END description=查看更新、state=本轮推荐已完成、ring=false，11项1失败。原生UI XML亦观察到查看更新。最终测试改为同时验证中心实心绘制与无忙碌描述，允许保留待查看更新；两轮终态均ring=false、无正在刷新。测试驱动保留一秒启动调度等待，因此不声称证明冷启动瞬间点击行为。

普通R8验证覆盖忙碌/结束绘制，不冒充真机转动帧率、网络刷新速度或耗电结论。原Debug动画/藏栏/生命周期/异常/请求合并及849 JVM、双Lint证据沿用此前最终生产源码验证，本轮未重跑全部JVM。

## 包核验与清理

5个模型文件大小/哈希及manifest、6个64位ELF LOAD段16KiB、ZIP对齐均通过；证书SHA256为原ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37。81个ZIP成员、395348385解压字节的已知明文凭据格式扫描0命中，不等于完整安全审计。

实际模拟器App和最终driver SHA256与归档逐一匹配。删除本轮/sdcard/qr-refresh-r8-diagnostic.xml，停止App并关闭专用模拟器，确认相关进程0；userdata保留。归档artifacts/local-ai/refresh-release-20261028，包含原失败、构建、审计、实际安装散列与cleanup.json。

仍开放：新版手机验证、虎扑/知乎真实受限权限、完整贴吧分页、独立去重质量及本人推荐收益、完整ARM高负载持续时间/回落、拔线耗电温升、长期缓存与完整出站审计。此候选不关闭整体发布门槛。
