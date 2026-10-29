# 收尾补充：正式构建阅读与刷新（2026-10-29）

## 对象与恢复

普通候选仍为 New-0.4.0-20261029-bad82db2.apk，SHA256 `bad82db27797d2e4b5726e6489c2c8af468a663d4bf15fe517f4871946ceb98c`。本轮未修改生产代码、替换候选、访问手机或发布。

上一轮构建日志停在 AndroidTest 编译，无成功终端。恢复时检查 Java/emulator 进程及两个指定 ADB serial，确认均不在运行后才重新构建和启动原 AVD，未清应用数据。恢复构建 `isolated-build-recovery.txt` 明确 BUILD SUCCESSFUL；原中断日志保留。

证据目录：`artifacts/local-ai/closeout-r8-reading-20261029/`。隔离包及驱动归档为 isolated-app.apk / isolated-test.apk，hash 见 isolated-package.json；当前关键源码指纹与上一轮候选相同，见 source-sha256.json。

## 结果

| 路径 | 结果 | 证据范围 |
| --- | --- | --- |
| 普通正式包刷新首次 | 5 检查、1 失败、raw 0 | 失败在前台焦点前提；dumpsys 定位 com.android.systemui 无响应弹窗，未执行刷新点击。保留 normal-refresh.txt，不计通过 |
| 恢复系统窗口后普通正式包刷新 | 11 检查、0 失败、raw -1 | normal-refresh-retry.txt；安装 base.apk 的 hash 与 bad82 完全一致，保留升级数据；48dp 目标、点击即忙碌环、重复点击与最终恢复实心圆点 |
| 同源非调试 R8 隔离包公开贴吧 | 15 检查、0 失败、raw -1 | r8-public-tieba.txt；真实来源与实际采集窗口、统一阅读器及正常阅读历史对照，无生产类反射、无 viewport 实验 hook |

三个日志的检查数和唯一终端代码均另行解析核对，见 runtime-summary.json；shell 退出成功没有被当成 instrumentation 成功。

测试结束已卸载两个临时 instrumentation 驱动，删除本次 UI dump，保留 App 数据并关闭两个专用 AVD；cleanup.json 记录卸载结果，stopped.json 记录停止后的两个 serial 均不可达。手机未访问，候选文件 hash 再核对未变。

贴吧本次独立来源窗口提供14条楼中楼，实际采集窗口13条，阅读器保留了这13条，涉及4个父楼层；身份、父子关系和来源创建时间一致，2张来源图片仍属于原父楼层，无横向溢出。两个来源窗口可因虚拟列表而不同，不宣称完整分页；公开页面仍有登录限制，不视为真实账号权限通过。

## 边界

刷新检查观察点击后的即时状态和最终状态，未验证手机上新列表绘制与动画结束的逐帧同步；本次最终状态文案为“尚未开始本轮整理”，不能据此证明列表已换批或内容供给刷新成功。

R8 实页检查不替代 Debug 受控延迟26秒接管测试，也不证明最新 R8 已重复相同时序测试。知乎/虎扑真实登录正文与评论权限、ARM 真机内存及长期耗电仍待实测。
