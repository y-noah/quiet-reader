# 设备测试终态校验 — 2026-10-27

## 本轮修改与证据

检查下一轮 ARM 内存验证工具时发现：instrumentation_summary 原先仅要求主机命令退出0、存在唯一非空 checks/failures 汇总且没有显式崩溃文字。Android instrumentation 返回取消码时，adb 仍可能退出0；最终断言之后中断也不能凭旧汇总判定通过。

measure_device_probe 现在使用 am instrument -w -r；解析器要求唯一 INSTRUMENTATION_CODE: -1。缺失、取消、重复或矛盾返回码均不通过。拔线测试由 DetachedProbeRecorder 保存的整数 resultCode=-1 作为独立终态证据，不伪造原始终端输出；同时出现文本返回码时拒绝混合来源。现有 COMPLETE、主机退出码、非空唯一汇总、失败和中止检查保持。

本轮运行 Python unittest discover -s scripts/models -p test_*.py：85项，全部通过，包含新增终态缺失/取消/重复/冲突、主机失败、中止、拔线记录返回码类型及来源混用检查。此计数是主机测试工具测试，不是85项Android或真机验证。

## 下一轮 ARM 验证准备结论

已有 ReleaseDiscoveryInstrumentation.model() 直接向真实 RankingService 发送 embedding/reranker 请求并检查实际结果，不经过个人向量缓存，因此不用删除个人数据来制造推理。但现有通用分支在推理前结束 DiscoveryActivity；它只能证明独立推理，不能用来证明首页/阅读/WebView并存时的整 App 峰值。

下一步应复用真实服务协议，制作有时限的测试专用并存工作负载：保留前台页面，确认两个模型真实输出及模型进程身份；对主进程、模型进程和可归属的隔离WebView进程做带进程身份前后核对的采样；单列缺失样本和阶段，不相加非同时高水位。不得清除个人偏好、向量缓存、登录信息或更改正常包诊断开关。已有隔离重放不冒充个人推荐收益。

本轮没有连接设备、启动模拟器、重建或安装APK、推送或发布。普通候选仍为cbe07180，手机仍为此前c050a471。ARM整体峰值、拔线能耗、实际账号权限及个人推荐质量仍待验证。
