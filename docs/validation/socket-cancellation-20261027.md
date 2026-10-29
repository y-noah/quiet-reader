# Android真实HTTPS取消验证（2026-10-27）

## 范围

此前受控Loader和伪HttpURLConnection只能证明取消调用，不能证明真实阻塞读取退出。本轮新增SocketCancellationProbe，在API36模拟器中使用生产RequestQueue、Android HttpsURLConnection和本地SSLServerSocket，分别卡住响应头、正文与register登记的外部读取线程，随后在主线程调用cancelPending。

正文场景须明确读到服务器首字节后才取消，不用请求已提交代替进入正文。服务端保留连接且不再发送数据，观察客户端读线程退出与对端EOF/连接关闭；同时验证取消调用不阻塞主线程、无旧响应发布、后续真实任务可执行。超时分别为主线程500ms、读取退出2500ms，网络读取自身超时12000ms。

本地测试证书存于androidTest/assets/loopback-cancellation-test.p12，口令loopback-test-only，是公开测试材料，无生产账号或权限；仅逐连接使用该证书的测试TrustManager，未设置全局信任策略、未绕过生产UrlPolicy、未放宽正式Manifest。ZIP检查确认正式App无此证书、测试APK含此证书，见fixture-packaging.json。

## 结果

证据目录：`artifacts/local-ai/socket-cancellation-20261027/`。

- 初版18/0通过后补强首个正文字节断言，再编译测试驱动，最终18/0且 `INSTRUMENTATION_CODE: -1`。初版和最终结果不累加成36项独立覆盖。
- 最终主线程取消均1ms；观察读线程和对端关闭分别3ms、2ms、3ms。这些是本地模拟器单次观察值，不能当作真机性能承诺或耗电数字。
- App SHA256 `8DC1524E63E269D6583FC4A7ACF968F22C05190207B928B19F6D0F4BFB4E22C8`，与上一轮生产代码一致；最终驱动 `0858B288ABDFD6834D21469BD3C18B51521C5E09AA6F1B3327A6AC9C722A591C`。本轮仅新增测试，无正式代码调整，所以不重复上一轮641项JVM/lint。
- 测试服务端和客户端执行器均关闭，停止隔离App与AVD，状态见cleanup.json。无手机访问、安装、推送或发布。

## 保留边界

本轮证明当前Android实现中RequestQueue能中止这些已建立的真实TLS读取，外部场景使用register真实路径，但没有创建WebView或证明真实网页销毁后所有Chromium联网停止，也未覆盖DNS、连接建立、TLS握手和复杂重定向期间的取消。没有通过Repository公共域名获取入口，保留其生产安全策略；不能将本地队列测试当作所有真实来源的端到端验收。真实来源访问、ARM整App≤1GB、非充电耗电/温升仍待验证。
