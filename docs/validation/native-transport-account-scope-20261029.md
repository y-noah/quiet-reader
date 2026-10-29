# 正文传输与账号缓存边界复验（2026-10-29）

## 本轮范围

手机已交还用户，本轮只用项目 API36 模拟器 emulator-5560。新增测试专用 `RepositoryTransportProbe` 与 FeedStoreInstrumentation 的 `repository-transport` 分支，没有修改生产代码、模型、候选包或账号策略。

探针调用真实 Repository.bytes/fetch，由 URLStreamHandler 截获 HTTPS 连接并返回受控 HttpURLConnection。两个公共数字地址避免 DNS；没有通过探针建立实际 TLS/socket。Cookie 是固定合成字段，不读取真实账号。这里验证的是生产请求代码设置的显式请求头、跳转、字节限制、取消与关闭行为，不能据此宣称实际线路抓包、系统 CookieHandler 行为或所有外联已经通过。

源码核对确认：正文 Cookie 只在原始精确主机上发送，跨主机不转发；图片单独按每个目标 URL 从 CookieManager 选择 Cookie，并跟踪中间跳转及来源账号指纹。没有为提高登录成功率而放宽到跨子域转发，因为这也会改变正文缓存的账号依赖边界。来源 WebView 仍可能请求平台第三方脚本/资源，这与本地阅读器自己的请求路径不同，未由本轮认证。

## 实际验证

隔离 Debug App 与驱动构建成功；安装后读取两者 base.apk 的 SHA256，与本地 APK 一致。App c117660a3d7fd01a9461f9c5f7699283c862b27384fc041d179a4cdbb2aca3b9；驱动 4fcb5a16d60e78812760169550e376c408027c7de1170528612d979bd986c334。未重跑模型或全量 JVM；本轮只增加诊断代码，不将此前 878 JVM/双 lint 记作本次重新运行。

| 运行 | 检查 | 失败 | 原始 instrumentation code |
| --- | ---: | ---: | ---: |
| repository-transport 首轮 | 22 | 0 | -1 |
| repository-transport 复跑 | 22 | 0 | -1 |
| reader-image-session | 13 | 0 | -1 |
| article-disk | 70 | 0 | -1 |
| cookie-rotation | 33 | 0 | -1 |

合计 160 检查。每份原始输出均单独检查 `checks`、`failures` 和原始 code，未仅凭 adb 进程退出 0 判断通过。原始结果、构建日志、源文件散列、安装包散列和清理记录位于 `artifacts/local-ai/native-transport-20261029`。

正文传输覆盖相对跳转、同主机保留原 Cookie、跨主机剥离、返回原主机恢复、禁用自动跳转、loopback/非 HTTPS 目标拒绝、循环最多五次、声明长度及未知长度流的字节上限、非成功状态和请求已取消时不建连接；各成功/失败路径核对已打开连接全部断开。没有覆盖所有响应状态、超时/断网时序、真实 TLS 重定向或全部讨论页 guard。

已有账号缓存探针复验真实 Android CookieManager：来源账号、中间图片目标及最终 CDN Cookie 改变时图片缓存失效；退出账号不复用旧图片，下载中切换拒绝迟到字节。正文内存、磁盘、历史和提取 token 的会话/namespace 检查通过；WebView 读取途中换账号时只重试一次且共享原截止时间，第二次变化终止，关闭后不继续请求。这些是合成 Cookie 与受控正文，不是知乎/虎扑真实受限内容的权限验收。

## 候选及清理

暂存普通候选仍 ec3f6a61，手机/current 仍 805b4ff9；本轮未更新手机或发布。测试驱动已卸载，目标 App 停止，模拟器退出且只读 CIM 确认任务 AVD 进程 0，保留 userdata。

本轮没有发现需要修改产品代码的新缺陷。真实账号 LOGIN_SEE、来源 WebView 外联、独立去重质量与本人推荐收益、正常整 App ARM 峰值/恢复、拔线能耗温升、长期缓存仍需后续证据；首页一划手感和刷新内容已换但动画继续的用户现象仍待最新版真机确认。
