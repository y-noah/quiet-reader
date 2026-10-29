# Find X7 Ultra真实推荐Controller与模型重叠内存（2026-10-28）

后续用户确认：体验优先，1GB改为优化目标，1.2GB若为短时峰值可以接受，覆盖本报告当时使用的1GB硬门槛。以下原始观测不改写；当前仍需确认高占用持续时间、回落及体验/能耗，不能据单个1.20GiB样本自动判可接受或必须牺牲体验压至1GB。最新约束以需求文档2026-10-28补充为准。

## 当前结论

真实推荐Controller可以工作，但整App资源门槛仍未通过。本轮稳定进程归属的完整PSS合计观测为1253930KiB（1224.54MiB，约1.20GiB），超过1GiB。不能用此前修复后不含模型的614MiB或本轮大量不含模型的样本关闭门槛。这个结果来自普通生产调度，不再只是直接绑定模型服务的诊断负载。

## 授权、包和流程

用户回复“已连接手机”后，ADB确认43062fa0/PHY110在线且已解锁。手机真实base.apk SHA256为8a5ca3f199879587d3a3cdb05edfcd553224a44f04e1d02b2694da73b8a2d883，未安装新App或修改其账号数据。使用此前普通非debuggable候选匹配签名的ReleaseRetainedRecommendationProbe，实际driver hash4a49f6dd76b021eb7937db0ba665b4a4d5152eba41b565197b7d2e250c42c242与归档一致。ColorOS安装完成页确认后点击实际“完成”按钮，没有关闭安全保护。

从真实设置→高级与诊断→本机推荐状态→继续整理发起普通Controller请求，不直接绑定模型服务、不清缓存、不写入候选/向量/分数/偏好。测试只读取自己数据库计数与App自己可见的worker PID，45秒有界观察。6检查/0失败、唯一raw-1：观察到1个worker；向量1590→1590、rerank245→249、active_snapshot721→731；feedback条数/profile版本未变。快照变化和四条新缓存说明该区间有正常工作，不证明个人收益或每次刷新都有新内容。

## 内存原始证据

同时用sample_retained_memory.py在90秒、1秒间隔只读采样。64个样本，46完整、18未知，其中1个完整含ranking。启用--require-model，实际返回0只代表采集覆盖，不代表预算通过。唯一完整模型重叠样本位于elapsedSeconds约46.157，前后PID/name/UID/startSeq归属稳定，包含：

| 进程 | PSS KiB | MiB |
| --- | ---: | ---: |
| app.quietreader / PID852 | 194373 | 189.82 |
| 归属New的WebView隔离renderer / PID944 | 447671 | 437.18 |
| app.quietreader:ranking / PID2926 | 611886 | 597.54 |
| 合计 | 1253930 | 1224.54 |

原始membership→PSS→membership及全部未知样本保存在artifacts/local-ai/phone-controller-20261028/memory/observations.jsonl。逐进程顺序采样，非严格同时、非连续真实高峰；system/GPU共享服务可能未归属，wholeAppPeakVerified始终false。此观测已与预算相矛盾，但不能称为所有使用场景的绝对最大值。主进程当时计数WebViews1，渲染进程Private Other很大；没有在本轮定位该WebView所有者或证明具体泄漏原因，不据此直接杀renderer或清网站状态。

## 收尾与后续

采样及instrumentation均已终态；结束时pidof app.quietreader:ranking无PID。临时app.quietreader.test卸载成功，包列表仅app.quietreader；/sdcard/qr-controller-install.xml删除，原screen_off_timeout30000恢复并读回，回到New首页。USB充电93%、26.9℃，不能用于拔线能耗或温升归因。已明确告知用户本轮用完、可拔线。早期USB未识别期间没有操作手机。

同期电脑上完成的排队推荐取消修复见[cancelled-ranking-input-20261028.md](cancelled-ranking-input-20261028.md)，尚未进入手机8a5；它消除取消后无效准备，不能声称已解决本轮1.20GiB模型重叠。下一步需区分模型加载/运行自身峰值与空闲renderer保留，优化后再验证真实普通Controller重叠；不得仅调低同UID采样阈值就宣布全App满足预算。既有受限登录正文、完整分页/表情、独立去重与本人推荐收益、长期缓存/出站及拔线能耗门槛继续开放。之前来源读取证据见[同日先前测试](phone-evening-20261028.md)。未发布。
