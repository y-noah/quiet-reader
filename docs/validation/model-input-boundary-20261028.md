# 模型最大输入边界 — 2026-10-28

仅测试驱动/主机测量工具扩展，生产模型、tokenizer、线程/批大小不变，普通手机仍f0。手机当前系统认证未解除，本轮全程不操作手机，在已有quiet-reader-api36/emulator-5560隔离x86_64 AVD执行，保留AVD数据。

## 实现核对

LocalModelEngine.embed在实际run前将输入截到128tokens并保留SEP；pairInput最长优先截断至含特殊符号256tokens。每次ONNX输入shape为[1,sequence]，不是48条同批tensor；RankingService虽然协议最多48条，但worker串行逐条处理，单请求合计字符<=50000、正文各<=4096、query<=512。模型仍一次加载一个，不能据此忽略切换时原生堆峰值或网页图片内存。

ReleaseDiscoveryInstrumentation 的foreground-models新增modelInput=boundary，限定既有两个隔离package。请求两个不同4096个中文字符的正文和512字符查询（最大单字段合法输入）；不改服务预算、不删除缓存或伪造向量/logit。要求真实embedding512维归一化、真实rerank有限logit、原始tokens>256、usedTokens恰为256且inputComplete=false，并逐ITEM核对前台焦点、结束解绑/模型退出。

主机measure_device_probe增加--model-input short|boundary，boundary只有foreground-models允许，在设备/输出访问之前拒绝其他phase；默认short不变。摘要保留输入模式。新增命令构造及提前拒绝回归，本轮相关53项通过（python-tests.txt），不累计旧数量。

## 实际结果

build.log完整成功，实际安装App e1d4fe13c0ad41c7971bab41332b32e01658e4496b1ce07bec18a2679eb55009，驱动f6637d9e7f6e04ea434429477a142ac8ee930749cb174753db268564b2be1351；手机未安装此驱动，模拟器实际两APK sha256sum与package-hashes.json匹配。

max-input-instrumentation.txt：26项/0失败、唯一raw code -1。两个长embedding真实归一化成功，两个长rerank真实有限logit且都符合256tokens截断报告。每种模型后仅驻留2000ms供采样，非推理耗时或用户加载延迟。

主机总观察19.953秒，12样本中9完整归属/3不完整，最高完整PSS714536KiB（约697.79MiB）。这是x86_64模拟器直接服务诊断，不能与Find X7 Ultra不同负载的737.45MiB对比成资源改善，也不证明ARM长输入/正常Controller/全App峰值或能耗已通过。采样顺序查询与间隙盲区仍在。

测试结束force-stop隔离App与驱动，已命名进程查询空；emu kill后第一次devices仍有条目，等待后再次核对devices-final不含emulator-5560、匹配AVD进程数0。没有因异步关闭就立即宣称退出，没有wipe userdata。summary.json逐项核对终态/实际包hash/最终关闭。

## 剩余范围

ARM最大输入实测与真实来源图文/评论在线峰值待手机自然解锁后继续；模拟器不能替代。手机认证状态不妨碍本地验证，但真实受限权限/拔线能耗/个人真实收益/独立去重和长期在线缓存仍开放。无生产参数变更、普通候选更新、推送或发布。
