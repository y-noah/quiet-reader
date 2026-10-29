# 明确接纳更新后的有限预读（2026-10-28）

用户要求打开、刷新、新页面等明确动作积极缓存，但不能无操作持续加载。检查发现主页更新按钮在已有新列表时只调用applyPending(false)；该方法也用于自动交付，不能统一赋予额度。旧20秒动作窗口结束后，点击更新虽接纳新列表，却要等后续滚动才预读。

DiscoveryController新增acceptUpdates，仅在未关闭、前台、存在待接纳结果时调用cacheAction并应用列表。DiscoveryActivity真实更新按钮及共用下拉刷新入口调用它。自动applyPending、accept、布局及正文完成回调保持不续期。继续使用既有20秒单窗口、不累加；不增加线程、后台任务或缓存配额，不修改排序/模型或登录。

## 修复前后证据

`artifacts/local-ai/explicit-feed-cache-action-20261028/`保存受控传输的真实Android Activity、SQLite快照、实际更新按钮测试。临时单源70条数据在启动时新鲜，避免真实网络刷新；ArticlePreloader.Loader受控，验证真实控制器调度，不代表公网加载速度或能耗。

- before.txt：5项2失败/code0。先确认前台真实加载器已工作，再显式清除旧动作额度；自动应用不获额度通过，点击更新无额度及不启动正文运输失败，后台拒绝通过。清除额度是可控前置条件，不冒称等待自然到期。
- after.txt：补自然到期断言后的6/0/code -1。点击后无需滚动启动加载，反复被动viewport回调经过完整20秒后额度失效、加载器不忙、无新请求；后台点击不启动工作。
- repeat.txt：同一最终驱动第二轮6/0/code -1；race-final.txt：修正旧字段依赖后，三个SQLite版本竞争场景15/0/code -1。
- lifecycle.txt：既有实际控制器生命周期32/0/code -1，包含换窗/取消/迟到结果、内存恢复、重建、HOME、实际模拟器息屏/唤醒、自然窗口到期与新滚动恢复。
- JVM 701项/107套，失败/错误/跳过0；Debug与Release lint成功。

## 测试维护与开放边界

首次pending-acceptance-race运行崩溃，crash.txt显示旧测试反射DiscoveryActivity.personalized字段，该字段已经不存在。只修改测试：核对控制器实际visibleMode为personal及原有快照ID/排序/SQLite活跃身份；没有恢复产品解释性文字。首次失败原样保留。

verification-summary.json核对各份日志唯一Instrumentation终态及包散列。崩溃留下的确切pending-race数据库及两个伴随文件已删除，本轮三个测试前缀无遗留（旧prefetch-disk数据保留），隔离App停止、模拟器正常关闭。没有清除普通App或手机数据。

最初Debug专项只在模拟器工作。后续R8实际接纳更新/阅读/反馈/双模型核心流程40/0，原签名普通包2c62→c315保留数据升级前12/0、升级后12/0、双模型19/0、复验12/0，终态及安装hash由r8-upgrade-summary.json核对。R8核心覆盖按钮实际接纳，有限额度/受控正文运输断言仍来自独立Debug专项，不将它们追认为R8同样专项。五项模型散列、六个64位ELF与ZIP16KiB对齐、原签名和DEBUG=false/measurement=false核对通过。

普通候选现为New-20261028-c315361c.apk，SHA256 c315361ca2de3cbc7ae383f1a9c0fc73a5d6424ed9f7e0fe93b229f10cd9ddc7，319466194字节。用户早间重新授权手机后实际f0→c315原位升级并保留账号数据，结束时驱动/临时文件已清，手机留最新包供日常使用；见[真机结果与原失败](phone-morning-20261028.md)。未推送、未发布。真实受限权限、连续ARM整App峰值、拔线耗电温升、长期缓存、用户实际推荐收益及其他完整验收门槛保持开放。
