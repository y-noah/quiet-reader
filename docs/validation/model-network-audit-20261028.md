# 本地双模型运行区间网络计量（2026-10-28）

## 方法与范围

仅扩展ReleaseDiscoveryInstrumentation的显式auditModelNetwork=true模式，限制在隔离app.quietreader.discoveryprobe，拒绝前台工作负载混用。沿原models-ui实际校验本地资源、绑定独立ranking进程、执行embedding和reranker、检查有限输出/512维归一化及真实JNI，释放并解绑等待worker退出。

首页正常finish、等待两秒后读取Android TrafficStats的本UID RX/TX，再等两秒要求计数稳定且受支持；之后绑定模型服务，完整执行两个模型，释放后额外等两秒再读计数。ranking为普通同UID私有进程，包含在同UID计数中。最终对www.example.com发一次明确HTTPS正对照（不带Cookie/偏好/新闻正文，响应最多32768字节，不记录正文），要求实际200、非空响应且RX/TX都增加，防止把无效计数器的零当作证据。正对照发生在模型测量区间之后。

这是指定区间Android同UID字节计数的观察，不是抓包/目的地明细，不识别无传输的失败连接尝试、计量未覆盖的接口、其他UID的隔离WebView或未来/不同输入路径。两秒计量等待不保证任意设备内核统计延迟均已结束，也不能用本实验认证全部隐私要求。

## 实际结果

API36 quiet-reader-api36/emulator-5554，nondebuggable隔离R8。first.txt和repeat.txt各23项/0失败、code -1。两轮实际双模型运行区间RX增量0/TX增量0；随后正对照各读577字节，Android计数RX增加6156/TX增加1368。相同数字是两轮原始输出，未以负对照输入或计数伪造结果。模型执行/归一化/返回logit/worker退出均沿原探针断言，不用模型文件存在替代运行。

build.log为初次网络计量构建，build-positive-control.log为最终有正对照版本，均终态成功。package.json归档最终App/驱动字节及SHA256，installed-hash.json实际拉取base.apk匹配。没有修改生产模型/网络实现或普通安全候选3e66b9b5，不将本隔离包替代待测普通包。

## 源码/备份核对

static-scope.json记录AI目录12个Java文件散列与限定模式搜索：无直接java.net/HttpURLConnection/URLConnection/okhttp/android.webkit/new Socket调用匹配；这不是传递依赖/ONNX原生二进制证明。生产Java日志调用仅Repository两处固定缓存清理失败说明，不包含动态异常、URL、账号或Cookie；不包含第三方库/Chromium/系统日志审计。

主manifest allowBackup=false/fullBackupContent=false、cleartext=false，ranking/Login/Settings/Main均nonexported，仅启动Discovery exported。data_extraction_rules排除cloud-backup与device-transfer的root/file/database/sharedpref/external域。已保留源码散列及R8编译manifest.txt，不把XML策略当作所有OEM真实备份导出实验已通过。当前源码未使用device-protected storage。

## 清理及未完成验收

无截图/账号/平台正文导出，无手机操作、安装、认证或设置修改。测试/App force-stop后关闭AVD，cleanup.json确认匹配进程0且ADB无emulator-5554，保留userdata。正对照仅访问公共example.com；没有放松HTTPS信任或URL策略。

本轮为本地双模型不产生可观测传输的运行证据，不能替代完整运行时出站审计（采集、真实登录、第三方源网页、媒体、异常/重试和跨账号），也不证明全部平台缓存无敏感信息或OEM备份绝不可导出。真实ARM峰值/拔线能耗/推荐与独立去重收益等原门槛保持开放；未推送/发布。
