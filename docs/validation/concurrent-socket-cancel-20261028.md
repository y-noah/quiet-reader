# 多条真实TLS连接同时取消（2026-10-28）

## 覆盖缺口与实现范围

现有SocketCancellationProbe只覆盖单条排队/外部拦截请求，本轮增加八条外部Ticket绑定真实Android HttpsURLConnection的并发场景：本地TLS服务器已发200与首字节x，其余100000字节正文故意不发送。每条客户端确认读到x后，服务器等待对端EOF/SocketException；两种动作分别为主线程cancelPending与close，不用仅中断线程或模拟disconnect调用次数代替真实网络断开。

测试仅修改SocketCancellationProbe。生产取消队列、线程数、超时和后台策略未修改，普通安全候选仍3e66b9b5，未更新手机或发版。TLS固定测试证书来自LoopbackTestTls，仅在测试客户端配置；没有放宽生产HTTPS信任/URL策略，也没有连接真实平台账号。

## 实际结果

专用quiet-reader-api36/API36，隔离debug app.quietreader.discoveryprobe。first.txt及repeat.txt各29项/0失败、code -1：原三个单连接场景18项，加八连接pause六项及close五项。两轮均在取消前八条真实正文读阻塞；取消后全部八个客户端退出、全部八个服务器对端观察关闭，无过期响应发布；pause后新真实请求仍可提交。两轮pause/close的主线程调用均1ms，观察到全部关闭均5ms。它们是这台模拟器与本地服务器的计时，不能推算手机/公网耗电或任意TLS实现的上限。

webview.txt另10项/0失败、code -1：实际Chromium不完整HTTPS正文，直接释放及HOME/onStop均关闭来源流、移除源WebView和保留共同阅读正文。HOME本轮对端观察关闭648ms。该原有受控生命周期探针使用debug生产对象反射，不冒充普通R8或平台真实账号验证。并发八连接直接测RequestQueue，不声称八条Chromium下载已同时运行或HOME已经覆盖全部并发场景。

最初误传phase参数而非FeedStoreInstrumentation的mode，实际执行默认独立存储回归172/0；已保存为wrong-mode-store.txt，未算入网络取消结论。更正mode后才执行上述29/29/10。无隐藏失败或生产代码修补。

构建终态成功，JVM682项失败/错误/跳过均0，jvm-summary.json保留。隔离App SHA256 `4b3e917e6cb50423344ddcf79c86fc3ecd94023a3d2359604c80284344408e71`，89266424字节；测试驱动 `5c7f1922c4171b74d93660cff6b6eb0d3559b7abdcae404fe61e7bdb0a69140d`。实际拉取base.apk匹配，package/installed-hash记录归档。debug包不含模型，不用于证明本机模型运行/资源。

## 清理及剩余验收

所有本地监听器/客户端/服务器执行器在finally关闭，测试临时读取Activity/SourceSurface释放。未导出截图或账号数据，没有手机测试文件。force-stop隔离App/驱动后关闭AVD；cleanup.json确认匹配进程0且ADB无emulator-5554，userdata保留。本轮未访问手机界面/认证或更改手机设置，未推送/发布。

本轮证明指定Android TLS取消路径在受控并发下退出并保持主线程可用。没有验证完整冷暖网络、所有Activity/模型流程、30分钟无周期唤醒、真实ARM整App峰值、拔线电量/温升或运行时全部出站目的地；这些仍按需求保持开放，不能把一次网络断开当作息屏零耗电结论。
