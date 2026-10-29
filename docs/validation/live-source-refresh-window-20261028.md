# 文字来源新窗口与连续刷新 / 2026-10-28

## 实际采集

API36 隔离模拟器 quiet-reader-api36 / emulator-5554 使用生产 HotSourceCollector 完成 live-text-sources。原始日志与公开候选位于 `artifacts/local-ai/live-source-window-20261028/`。completed=true、storageErrors=0、INSTRUMENTATION_CODE=-1；来源可用性为9/10，不以测试退出码替代来源状态。

知乎30、微博50、虎扑35、财联社20、IT之家12、澎湃20、BBC中文10、百度50、贴吧30，共257候选。V2EX为LOGIN_REQUIRED、0条，错误文案同时涵盖来源登录要求和访问限制；未操作账号，不能认定是用户手机登录失效，也不据此删除来源。

corpus.json capturedAt=1793124526671，SHA256 `95983108e20cb20e6e96a890849a2d35bc31966e325a62e63b0327dd4aaf12dc`。对比上一窗口SHA256 `64efd7b0dddab0102fce0e5bd3093d2b41879cc99e5b5ebc9dacc8ae39261894`，按来源+精确标题有90条重合。扫描JVM及Android assets共9份 observed-event 夹具，39条命中已标注标题；排除旧窗口及夹具联合集合后160条未见精确标题，明细见 corpus-comparison.json / unseen-exact-titles.json。不是160个独立新事件，也没有新增用户兴趣标签或计算去重质量。原采集的算法合并统计不是人工事件判断。

## 测试报告修正及连续刷新

LiveTextRefreshProbe 的报告分母此前写死9，改为 HotSourceCatalog.TEXT_HOT.size()，不修改生产逻辑、来源开关或普通候选。隔离构建成功；679项JVM测试0失败/错误/跳过（Gradle可能复用未变任务，未声称本轮全部重新执行）。编译日志 build-refresh.log，机器汇总 unit-test-summary.json。

实际安装App SHA256 `a5c8d38074525d996883f9fb200047317b955828271c56a654183e21798956ba`，与采集使用的生产App字节相同；新驱动 `a5a816362667a0db0cda4c6b9baa288aeeee8c7db48a030ec51373ffdd9c32f0`。安装命令两次Success，保存 refresh-app.apk / refresh-test.apk；本轮未拉取已安装APK做第二次哈希证明。

live-text-refresh 使用新的隔离数据库运行两次实际网络采集，再对同一数据库注入失败的离线传输。refresh-matrix.txt 保存原始63项/0失败及code -1，refresh-matrix.json保存逐来源状态。两轮均9/10 READY；九个来源requested/committed从1增长到2，成功采集时间推进，已有条目的first_seen保持。九个内容摘要哈希相同不代表上游陈旧或内容必然最新，只证明实际请求与成功提交再次完成。V2EX处于退避，第二轮没有请求，保持LOGIN_REQUIRED/0；secondNewAttempts明确为9/10。

注入离线阶段实际发起9次请求，九个来源变为OFFLINE，原条目、内容哈希及上次成功时间不变；V2EX继续退避，无离线请求，不能声称V2EX离线缓存验证通过。这一阶段是受控故障，不是实际断网或拔线耗电验证。

## 清理及剩余边界

导出的两个明确模拟器路径已删除，隔离App/驱动force-stop，模拟器正常关闭。cleanup.json确认匹配AVD进程0且ADB序列消失，userdata保留；未操作手机、修改手机设置、安装普通候选、推送或发布。

当前普通候选仍为New-20261028-06636775.apk，手机仍f0d4dd5a。本轮补强来源可用性、刷新和故障保留证据，不关闭真实受限账号权限、完整贴吧楼中楼及分页、ARM真实网络全App峰值、非充电耗电温升、长期在线缓存、本人推荐收益、独立未知事件去重、全部页面视觉/TalkBack和完整网络隐私审计门槛。
