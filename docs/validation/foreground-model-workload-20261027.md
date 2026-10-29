# 前台实际双模型工作负载 — 2026-10-27

## 范围

仅修改测试APK和主机测试工具。ReleaseDiscoveryInstrumentation新增foreground-models阶段，限制app.quietreader.flowprobe/discoveryprobe隔离目标；保留真实DiscoveryActivity前台，通过既有Messenger协议各提交两条embedding/reranker输入。不经个人向量/精排缓存，不删除任何缓存来制造推理。检查512维归一化向量、有限精排logit、请求身份和完成数量；每个实际ITEM回调核对首页仍有窗口焦点；释放并解绑后确认模型进程退出。沿用每批30秒等待上限，无生产入口/线程/定时任务变化。

这是直接服务协议的诊断工作负载，不是正常推荐控制器调度的端到端证明，不是持续高负载或真实用户画像。不得为了测峰值改成阅读与模型强行并发：当前正文导航本就应停止模型，独立切换验证仍有效。

measure_device_probe增加foreground-models选择，并复用已有进程归属解析器。每轮meminfo前后分别读取activity processes，核对PID/name/UID/startSeq及精确packageList成员。漏掉所属隔离WebView、进程出生/退出或PID复用时归属总数为空；保留原命名进程下界并单独报告完整/不完整归属样本。报告schema3保留原字段；原始两份名册与内存输出保留供复核。查询为顺序采样，不称同时瞬间或连续峰值。

## 运行证据

目录artifacts/local-ai/foreground-model-workload-20261027/。

- build.log和build-focus-wait.log均成功。首轮24项/1失败/返回0：诊断弹窗刚关闭时检查焦点过早；四个真实模型ITEM时焦点均在首页。记录foreground-*保留，不当通过。驱动增加最多5秒焦点等待后重建，未修改生产代码或放宽每个真实ITEM的前台断言。
- 最终foreground-focus-wait-*：24项/0失败，INSTRUMENTATION_CODE:-1，实际embedding/reranker输出和释放退出通过。主机工具最终87项/0失败，python-tests-final.txt。期间测试编辑误将旧断言放到新失败场景中造成一项主机失败，已归回原测试后完整重跑；不将该次失败计为通过。
- 最终6轮内存样本：4轮命名进程可读、3轮完整归属、3轮不完整归属。最大完整归属374498KiB；这是x86_64模拟器、两个短输入的定期观察，不是Find X7 Ultra ARM结果或整App峰值≤1GiB的验收。首轮更高下界属于另一次失败运行，不能与最终样本混用。
- packages.json记录本轮隔离R8及驱动SHA；普通候选未重建或修改。首次本地非提权ADB未设ANDROID_USER_HOME导致初始化目录权限失败，随后明确设置现有工具目录后运行成功；没有因此枚举或连接手机。

测试结束force-stop两个明确隔离包，emu kill关闭emulator-5560，保留AVD数据。cleanup.json为关闭后命名进程复核。不改设备显示设置，不操作手机，不上传/推送/发布。

## 尚待证据

可以在后续获准的手机轮次复用有界隔离工作负载，避免只有缓存命中的情况；仍须结合正常控制器实际工作、可归属WebView完整性和更长正常使用范围。当前短批输出检查不保证定期采样恰好覆盖每个模型推理瞬间，更不能据模拟器数值承诺手机峰值或耗电。

真实账号完整权限、逐条贴吧身份关系、ARM整App峰值、拔线耗电温升、长期磁盘软淘汰和本人连续推荐收益仍开放。普通候选仍cbe07180，手机此前c050a471。


# 2026-10-27 手机ARM推理与息屏资源

普通f0已安装且保留用户数据。息屏两快照82秒主进程CPU仅增加0.06秒，PSS84.46→65.45MiB，无模型/所属WebView端点成员；不当耗电或持续峰值证明。另用同源码隔离R8包在Find X7 Ultra真实双模型推理：冷首轮25/0、4轮驻留最终72/0/code-1，18份完整归属样本最大744994KiB（727.53MiB），包含当时主进程/模型/所属WebView。并非正常Controller全负载或全部场景峰值认证。实际两APK hash匹配；隔离App及驱动均已卸载，原亮屏设置0恢复、返回普通New息屏Dozing。仅测试工具增加有界采样参数，主机87项通过；未更改普通候选/发布。详见 [整夜手机证据](./phone-night-20261027.md)。
