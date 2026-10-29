# 真实来源长列表自动扩窗与位置恢复（2026-10-28）

## 范围

本轮只修改LiveDiscoverySupplyProbe测试驱动。专用API36模拟器、隔离discoveryprobe包和随机test-ui-live-supply数据库，使用产品默认来源采集、分组和原生卡片，不注入候选、正文或个人标签。来源采集完成后暂停推荐/预读并应用完成版本，隔离验证列表行为。未操作连接的手机，也没有请求或重试系统认证。

驱动通过ListView.setSelectionFromTop移到接近当前渲染范围底部，让产品onScroll自动扩窗，两次均不点按钮。它证明布局回调自动扩窗，不是手势流畅度、连续帧耗时、耗电或真机触摸验收。核对第100条实际卡片标题、可见视图回收，再进入设置返回和重建Activity，要求同eventId及像素偏移（容差1px）恢复，重建后仍保留扩展范围。

## 原失败与测试修正

原驱动仍等待已删除的“查看更多话题”并点击，已换成真实自动扩窗路径。首次run1.txt两次扩窗通过，但要求第100条必须正好位于视口顶部的等待超时，8项/1失败/code0。原报告和原驱动APK保留。此等待条件超出了“该条可见”的需求；改成按实际firstVisiblePosition查找第100条可见子View后重跑。没有修改产品列表逻辑，也没有确证首次超时的具体布局时序，因此不把这次测试修正算作产品故障修复。

## 最终证据

目录artifacts/local-ai/live-discovery-flow-20261028/。

- run2.txt：239条，12项/0失败/INSTRUMENTATION_CODE -1。
- run3.txt：268条，12项/0失败/INSTRUMENTATION_CODE -1。公开来源窗口变化，虎扑主条数20→49；不是固定快照的重复播放。
- 两轮可见主来源包括微博、知乎、虎扑、财联社、IT之家、澎湃、BBC中文、百度、贴吧。资格检查覆盖产品文本/中文/默认来源准入；eventId唯一不等于语义去重质量或用户感兴趣。
- run2-page100.png、run3-page100.png已逐张人工查看：混合来源卡片、短摘要、来源行和菜单正常显示，无旧查看更多按钮；截图不能替代全部页面或无障碍检查。
- build日志终态成功，最终rebuild.log成功编译测试；本轮没有生产源码修改，没有声称重新执行全部JVM或Lint。
- app.apk和实际安装installed-hash.txt均ea01ca9f414f5aa79b2bd2431acf41e8b746e85c96d9224c3cafbac06748884a；首次test.apk及修正后corrected-test.apk散列见packages.json。修正后测试SHA256为04759469db2671a2b060885f776f49a0472f3ee14718bca87bad5498a70c1b9f。
- 截图已从模拟器导出后删除；databases-after.txt无本轮test-ui-live-supply数据库。其余已有测试数据库未擅自清除。模拟器正常退出，终态核对见cleanup.json；保留AVD用户数据。

## 尚未完成

普通候选仍为a5cd0ddb，手机仍f0d4dd5a；本轮无安装手机、推送或发布。未关闭真实受限虎扑/知乎权限、完整贴吧分页、多来源小时/天级缓存、ARM连续整App峰值、拔线耗电/温升、用户推荐收益、独立语义去重和全页面/出站审计门槛。
