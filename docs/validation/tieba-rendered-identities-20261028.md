# 2026-10-28 贴吧真实网页楼中楼身份与时间

## 公网页面发现的问题

匿名 WebView 打开 https://tieba.baidu.com/p/11139792603，正文可读，但八条楼中楼缺少 data-id；旧实现因此没有启动可选时间接口。此前 live-round1、diagnostic、identity、component、vue2、component-time 的失败日志保留，不能归为接口超时或测试成功。

公开页面 Vue2 组件的精确根节点携带 commentData.id/time 与 pid。前两条与独立公开 totalComment 响应逐项对应，见 component-api-identity.json、public-response-summary.json。未读取全局账号状态或导出作者、正文对象。

## 实现

TiebaRenderedReplies 只读取当前前台、同源、精确组件根节点上的公开回复标识与时间；父 ID 必须匹配实际父楼 DOM，冲突原 ID、错误根、重复 ID 均拒绝。每次快照清理旧注解，防止虚拟节点回收串用。没有增加请求、定时器、模型。原可选接口仅作为无直接时间时的后备，保持既有大小、时限、登录门槛与复用限制。

可见公开楼中楼的来源时间可在后续评论登录门槛存在时保存；这不会解除后续加载限制，也不能据此生成讨论进展重推。父楼时间保持未知。

关系版本升为 4：旧缓存可离线阅读，在线需重新获取；旧版不支持自动续载，合并两端都需当前版本，避免旧回退身份与新真实身份重复入列。回归覆盖历史保存、正文无损及双方向拒绝合并。

## 最终证据

- continuation-build.txt：Debug 构建、Debug/Release Lint 成功；845 JVM / 118 套，失败、错误、跳过均 0。摘要和专项 XML 已归档。
- rendered-controlled.txt：真实 Chromium、受控网络与组件，18 检查 / 0 失败，唯一 raw -1。覆盖无附加请求、精确父子关系、回收节点清旧标识、错误父 ID、重复 ID、登录限制不变、取消/超量/复用。
- rendered-live.txt：真正匿名公网页面可读 1/1，probeErrors=0，唯一 raw -1。10 sections、8 子回复，其中 4 条有可验证来源时间，历史精确恢复 4 条；父楼时间 0。moreStatus=login，restrictedProgressRejected=true。不能声称全部 8 条已取得时间，另外 4 条原因仍待细查；缺失时间不参与进展证明。
- rendered-tieba-virtual-replies.txt：13/0；rendered-tieba-discussion-progress.txt：20/0；均唯一 raw -1。前者受控虚拟列表，后者受控进展与账号切换，不代表真实受限登录分页。
- rendered-installed.json：实际模拟器安装包与归档 App/driver SHA256 一致。

## 仍未完成

本轮未做新增实现的 R8 公网回归、普通原签名保数据升级或真机验证。当前普通候选仍 9e06075e，不含本轮改动；手机 c315361c 未操作，未发布。真实知乎/虎扑登录、贴吧受限分页、独立推荐与去重质量、ARM 整 App 峰值、拔线耗电温升和长期缓存仍开放。

测试归档位于 artifacts/local-ai/tieba-live-times-20261028；模拟器关闭且保留 userdata，终态另见 rendered-cleanup.json。

## 后续：四条缺失时间已定位并修复

见 [tieba-image-parent-floor-20261028.md](tieba-image-parent-floor-20261028.md)。另外四条实际来源时间齐全；旧规则遗漏直接兄弟 image-card-wrapper，导致图片父楼及其父子映射丢失。修复后同一实际页面两个父楼、两张父层图片、八条父子匹配、八条来源时间与历史精确恢复通过。原始四条结果保留，不追改历史日志；R8和普通升级尚未完成。
