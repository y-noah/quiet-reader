# 安卓冗余实现清理补充（2026-10-26）

本次承接旧界面清理。用户要求删除安卓包冗余代码，不接受只隐藏入口。源码保留本地，不推送、不发布、不替换手机。

## 删除与保留

- DiscoveryController 删除稍后读监听、读取、保存、取消保存以及相应重排/提示分支；启动时不再读取旧 MainActivity 收藏偏好或执行收藏导入。
- FeedStore 删除 save、unsave、saveWithInterest、unsaveWithInterest、saved、importLegacySaved 六个不再被产品调用的接口。
- Repository 删除每次启动解析和改写旧收藏 SharedPreferences 的分支。
- DiscoveryActivity 与 Controller 删除仅赋值、没有消费者的 personalized 状态及回调。
- SavedInterestProbe 删除：不再测试已经取消的新增收藏/取消收藏功能。旧导入接口测试同步删除。
- 历史数据库保留验证使用的少量造数及读取操作归入 androidTest/discovery/LegacySavedFixture，不进入主源码或 App APK。相关升级、缓存清理、偏好重置测试仍验证历史数据保留。
- SAVED 历史反馈的解析、显示、撤销及数据库表结构保留：现有数据库可能包含这些记录，不能为删代码而破坏旧数据。没有新增收藏入口或后台导入任务。
- 前一轮已删除旧榜单、平台底栏、收藏页、NavigationScroll、PlatformMarkView 等，详见 legacy-interface-cleanup-20261026.md。

## 验证

- android-redundancy-cleanup-20261026-build-fixed.txt：Debug 应用/测试包、577 项 JVM 测试、lintDebug、Release R8、lintRelease 通过；95 个测试报告，失败/错误/跳过均为 0。Lint 通过不表示没有警告。
- android-redundancy-cleanup-20261026-package-audit.txt：直接读取未压缩 Debug APK 的全部 DEX，旧导航类、旧跳转标识、上述收藏专用接口及测试辅助类均不存在；未发现测试图片目录或探针结果文件进入 APK。直接检查 Debug 包避免把 R8 的删除结果当成源码已经清理。
- 隔离模拟器 emulator-5560 / quiet-reader-api36（qemu=1），包 app.quietreader.discoveryprobe。未访问手机。
- legacy-navigation-removal：19/0；platform-preference：24/0。确认当前设置/来源/返回入口，历史来源偏好迁移与撤销，以及历史数据保留。

## 失败记录与边界

首轮测试包编译发现 PlatformPreferenceProbe 仍调用已删除的 unsaveWithInterest。将该旧场景迁移为真实 feedback/undo 接口验证历史记录撤销，保留初次失败日志 android-redundancy-cleanup-20261026-build.txt，修正后编译通过。未恢复生产废弃接口以迁就旧测试。

这不是“整个项目没有任何可改进代码”的证明，也不是五项新阅读需求全部完成。当前源码仍包含前一轮精简页面和自动续读改动；贴吧热搜直达正文待实现，自动续读大字号错误重试运行验收仍开放。手机仍是 ee26445e 候选，未安装这次源码。

## 存储回归补充

android-redundancy-cleanup-20261026-store.txt：157/0。默认存储回归覆盖来源开关、数据版本迁移、压缩与清理、历史数据保留、阅读反馈与兴趣设置等受控场景。本轮三组模拟器检查合计 200/0；结束后 force-stop 隔离应用。没有清除用户手机数据。
