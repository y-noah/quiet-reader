# 图片共享所有权与真实TLS取消组合验证（2026-10-28）

既有ReaderImageScopeTest用受控连接验证订阅共享，SocketCancellationProbe用真实Android TLS验证RequestQueue取消。本轮新增SharedImageSocketProbe，将生产ReaderImages.Scope/共享flight/会话校验/内存及磁盘缓存与真实Android HttpsURLConnection组合，不仅检查模拟disconnect调用。生产代码没有修改。

## 场景与实测

FeedStoreInstrumentation的mode=shared-image-sockets，仅用于隔离debug模拟器包。本地TLS服务器给八个不同图片请求分别发送200响应与8字节PNG签名，其余正文不发送；客户端全部读到签名后确认仍未完成。第二个Scope加入第0张图片，九个订阅保持八次实际获取。

主线程关闭第一个Scope后，服务器观察七个独占连接EOF/SocketException，共享连接仍未关闭，第二订阅未完成且没有重启下载。最后关闭第二个Scope，剩余服务器对端观察关闭，两个共享订阅均拒绝返回字节，所有订阅清空。磁盘此时无图片或partial记录。

接着新Scope必须进行第九次实际获取，服务端返回完整PNG；实际BitmapFactory解码为16像素。换新的ReaderImages及ImageDiskCache实例，禁止网络fetcher后仍能从磁盘取得同一PNG。这样既检查中止数据不作为RAM命中，也检查后续正常缓存未被取消逻辑破坏。

证据 `artifacts/local-ai/shared-image-sockets-20261028/`：first.txt和repeat.txt各21/0、唯一code -1。首轮主线程close观测5/2ms，复跑3/0ms，仅是该模拟器本地计时。实际App和测试驱动散列与package.json一致；698JVM/107套无失败/错误/跳过。构建终态成功，verification-summary.json严格核对结果。

## 边界和清理

网络fetcher由测试注入，以便使用仅测试客户端信任的loopback证书；没有修改全局TLS信任或生产UrlPolicy，未验证生产Repository.imageBytes的公网来源/重定向/认证路径。本轮直接操作真实Scope，并非实际Activity/HOME/息屏；不能与先前独立页面生命周期测试拼成未执行的完整端到端场景，也不能由2次短连接取消推出整App峰值或拔线耗电结论。

取消发生在不完整网络正文等待阶段，不证明任意磁盘写入瞬间取消的回滚。使用同一URL不同使用者，未作真实账号切换/私有图片授权等价性验证。主机工具的网络和模拟器开销不代表ARM手机表现。

每轮finally关闭监听器、Scope和测试执行器并删除唯一测试图片目录；实际run-as限定目录查询确认无test-shared-image-*残留。隔离App/驱动force-stop后正常关闭AVD，保留已有AVD数据库/模型，不宣称清空全部测试数据。本轮未唤醒/操作手机，没有手机临时文件。普通候选仍f43a3db6、手机仍f0d4dd5a，未发布。

此次组合测试未发现需要修改生产代码的新缺陷。整体真实账号、连续ARM资源/拔线能耗、长期在线满额缓存、本人推荐收益和独立去重/完整可达性等门槛继续开放。
