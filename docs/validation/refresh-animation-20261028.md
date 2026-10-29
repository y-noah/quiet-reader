# 刷新状态动画（2026-10-28）

后续普通原签名候选已更新e84c1cb3，压缩包刷新两轮11/0、升级保留与双模型通过；手机仍8a5，未发布。以下“尚未普通候选”描述为本次Debug验证结束时的历史状态，续验见[普通包证据](refresh-release-20261028.md)。

用户反馈刷新没有可见反馈，容易连点。顶部原圆点在刷新期间变为20dp旋转圆环，完成/失败/取消恢复圆点，48dp点击区及任何时候回顶部行为保留。点击后的本地换批/SQLite等待也通过Controller.refreshWork立即通知，不必等到联网才显示；来源采集继续使用原loading状态。连点沿原explicitRefreshRunning守卫合并请求，没有新增刷新按钮、网络请求或额外模型工作。

新增RefreshIndicatorDrawable只绘制小圆点/圆环，不改变布局。ValueAnimator仅在首页前台有焦点且顶部栏显示、确有刷新工作时运行；暂停、焦点离开、前向滚动藏栏、完成或失败取消帧，反向显示才恢复。系统关闭动画时保留静态忙碌圆环，遵守系统动画开关；没有加维持动画的定时轮询。通用storage错误还会作废本轮explicitRefresh，防止错误后忙碌状态残留。取消与画像/缓存策略未放宽。

## 实际验证

- 最终生产源码下849 JVM/118套0失败、错误、跳过，lintDebug/lintRelease通过，build-final.txt终态成功。
- 隔离API36模拟器refresh-animation：初始9项、最终加真实ListView平滑滚动回调后11项均0失败、唯一raw-1。源采集转动及角度推进、48dp点击区、藏栏停止/反向恢复、pause/resume回调、源完成恢复、真实本地请求排队期间立即动画、三击仅一个epoch并回顶部、实际注入storage异常后恢复圆点/保留列表均核对。生命周期部分调用Instrumentation生命周期入口，不冒充真机切后台。
- 原下拉接收进行中的pending内容6/0、取消曝光与成功新曝光5/0，唯一raw-1；保留原取消/不重复请求语义。
- 首轮前置focused list失败0检查/1失败。随后真实UI树是Android的“System UI isn't responding”，点击实际Wait后同一App/driver通过，没有放宽前置检查。系统lastanr为空不意味着弹窗不存在，原失败与XML保留。
- 首次idle截图在drawable状态切换的绘制帧之前捕获，仍看到旧圆环。只给截图驱动增加两次postOnAnimation等待及busy=false断言，未改生产完成条件；最终final-idle.png实际可见圆点，busy截图实际显示圆环，人工查看。后续滚动驱动构建一次原生路径拼写错误失败，纠正已有路径后成功，原日志保留。

证据artifacts/local-ai/refresh-animation-20261028，实际安装App/最终scroll-test哈希核对在installed-hashes.json；生产App在测试驱动迭代时未重新安装不同字节。测试使用合成独立数据库及受控异常，不含个人数据/真实平台请求，不是公网刷新速度、真机帧率/耗电或R8普通升级验收。模拟器临时截图及诊断XML删除并退出，保留userdata；本轮未访问手机。普通原签名安全候选/手机仍8a5，尚未包含刷新动画与前轮排队取消修复，未发布。
