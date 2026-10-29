# 预读失败冷却的账号与缓存边界（2026-10-29）

## 问题及范围

`ArticlePreloader` 原先按来源、URL、候选版本和所选帖子记录五分钟失败冷却。登录/账号变化或清理正文缓存后，旧失败仍会阻止同一内容预读，最长五分钟。此问题不等同于平台真实登录权限问题。

修复保留原有最多 64 项冷却记录，每项另存同一次 `ArticleSession` 捕获的缓存 namespace 与账号 Cookie 指纹。仅同 scope 的失败继续冷却；账号或 namespace 变化后的下一次用户动作可立即重新尝试。使用单调时钟计算间隔。先检查可用缓存，再判断冷却；成功正文与主动取消继续移除原记录。

会话在候选资格检查后捕获一次，缓存查找与 activeSession 复用它，避免为冷却候选重复读取 Cookie。无明文 Cookie 持久化、定时重试、新增后台额度、网络轮询或模型调用。账号指纹规则不变，知乎 `_xsrf` 变化不视为账号切换。

## 复现和修复验证

证据目录：`artifacts/local-ai/prefetch-account-cooldown-20261029`。

`PrefetchAccountCooldownProbe` 使用专用 Android 16 模拟器、真实 CookieManager 与路径隔离的合成 Cookie，正文 transport 为受控失败，不访问来源或登录真实账号。修复前 `before.txt` 为 9 项/2 失败/raw code 0：账号变化和 namespace 变化均未立即重试；其余已执行项通过。namespace 再失败冷却为条件检查，旧实现未执行，不能算通过。

修复后 `final.txt` 为 10 项/0 失败/raw code -1：首次请求、同账号冷却、普通请求上下文旋转仍冷却、账号变化立即重试、namespace 变化立即重试、新 scope 再失败冷却、暂停无自动工作、另一文章不受阻、恢复 namespace。探针只清理自身路径的两项 Cookie，等待 Controller 存储线程退出后删除测试数据库。

隔离参数全量 893 JVM/126 套、0 失败/错误/跳过，lintDebug/lintRelease 与 Debug App/driver 编译成功，见 `build-final.log`。磁盘复用 19/0、正文预读 21/0。

两项旧测试前置条件与本轮有限首页要求不符，保留原始失败：生命周期测试仍跳第 46 条并断言该 URL，32 项/1 失败；待换批预读仍等待首页显示全部 70 条，0 项/1 错误。仅修改测试为当前有限批次末条和非空且不超过 FeedBatch.SIZE 的实际首页，不扩大生产列表。修正后生命周期 32/0、待换批预读 13/0/raw code -1。有限首页批次 10/0，最终 Debug 六阶段共 105 项/0 失败，均 raw code -1。

保留 userdata 的升级模拟器实际旧包散列为 f19d4d80。升级前首次 12 项/1 失败是原合成 Cookie 的一天 TTL 已过；保留 `upgrade-before.txt`。既有严格续期阶段确认 TTL 已过且专用测试域为空后，只续期该合成 Cookie（8/0），其他偏好、数据库、阅读位置未重种；重新基线 12/0。后续保留的是续期后的测试会话，不能称原 Cookie 跨过其过期时间，也不证明真实账号权限。模拟器首次 40 秒就绪观察超时，继续等同一实例后 boot/user/CE 可用，无重复启动或清数据。

首页字号沿用本轮已验证的首条标题 17sp、其他 16sp、简介 12sp、来源 11sp；正文与 48dp 点击目标保持。具体视觉验证见 `trailing-question-20261029.md`。

## R8、普通升级和候选

R8 实际 Controller 的待接收预读/磁盘复用 10/0、推荐式换批/真实呈现后结束动画/底部不追加 10/0，均 raw code -1。此处是成功预读管线验证；合成账号切换专项只在 Debug 执行，不扩大为 R8 真实账号切换证明。

普通原签名候选保留 userdata 升级：续期后的基线 12/0、升级后 12/0、双模型/JNI 与 worker 退出 19/0、模型后再检查 12/0，共 55 项/0 失败/raw code -1。四个实际安装 App/driver SHA256 均与受测文件一致。五模型与 manifest、六个 64 位 ELF 的 16KiB LOAD、ZIP 对齐、原证书检查通过；81 ZIP 成员已知明文凭据模式扫描无发现，不代表完整外联审计。

暂存 `New-20261029-305c25af.apk`，319498962 字节，SHA256 `305c25af05027abd418ff7e17c7a589648c4c05fafdcf03febe9695fc3fcf3a3`。app.quietreader / 47 / 0.4.0-upgrade-probe，原证书 SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`。安全副本 hash 相同，仅 staged-candidate 更新；current/手机保持 805b4ff9。phoneInstalled/published/phoneValidationComplete 均 false。两台专用模拟器临时驱动已卸载、App停止、AVD关闭，保留 userdata。本轮相关探针无公共导出文件。

## 验收边界

上述检查仅证明本地预读冷却和缓存作用域，不证明真实知乎/虎扑受限内容、全分页权限或整体能耗。真实手机登录、个人推荐收益、独立去重质量、ARM 峰值/回落、拔线耗电温升、长期缓存与完整外联审计仍开放。本轮手机未访问，未发布。
