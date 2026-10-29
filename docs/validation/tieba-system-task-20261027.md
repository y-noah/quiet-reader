# 贴吧显式选择与真实进程死亡恢复（2026-10-27）

本轮目标是检查选帖后退出后台、进程终止、从系统最近任务返回的实际行为。仅使用 emulator-5560 / quiet-reader-api36 / app.quietreader.discoveryprobe。手机普通包 ee26445e 未变；不推送或发版。

## 发现的生产交互缺陷及修改

历史恢复可显示第二帖正文，但当前请求仍可能是默认话题请求。菜单选择该正在显示的第二帖时，旧 topicMenu 因正文来源相同而完全忽略点击，导致后续重新读取仍按默认首帖处理。normal-g 主机只读检查确认：正文第二帖、明确点击第二帖后，current.selectedTopicPost 仍为空。

MainActivity 只修改此菜单分支：正文已显示但请求尚未明确选择时，仍用同一根话题的显式请求重新打开；已明确选择同一帖时保持不重复打开。沿用原缓存/历史/位置/学习资格逻辑，不直接修改图片所有权或绕开官方列表校验。受控菜单新增：首次明确选择当前默认首帖后 current 绑定该帖、来源 HTTP 请求数仍0。随后切换第二帖、旋转、原候选学习、失败返回等旧检查保留。

## 如何避免测试框架影响恢复

最初 a～d 两段 instrumentation 记录正文和位置恢复、但显式选择丢失。d 轮系统停止态实际 mHaveState=true，而第二段 instrumentation 的 Activity 收到 ANDROID_STATE null。启动第二段 instrumentation 会重启目标包，不能将该结果当普通进程回收生产缺陷；未据此增加生产持久化代码。

最终使用以下路径：

1. instrumentation 仅准备受控帖子、实际菜单选帖与缓存，HOME后发送11/0状态并保持存活；主机核对PID，通过run-as仅终止隔离App UID下的该进程。此段仅引导，不算最终普通进程恢复。
2. 从模拟器实际最近任务卡片打开隔离Debug App，使用UI树找到真实菜单，明确选择第二帖，再通过实际WebView上的滑动建立普通前台阅读位置。
3. 主机 JDK JDI 只读检查当前普通进程的选帖、正文、候选、空回退栈及非初始位置，不调用App方法或修改字段；读取后台持久化的位置及bodySource，与实际滚动位置严格比较。
4. HOME后终止这个非instrumentation进程，核对PID消失；通过最近任务重新打开。新PID必须不同，再只读等待前台Activity及页面就绪，检查同一话题/选帖/正文/候选和位置。
5. 最后才启动独立清理instrumentation，恢复测试来源配置、删除本轮临时数据库和历史项，停止隔离进程。JDI转发及模拟器临时UI XML一并移除。

主机工具为 scripts/test-tieba-process.ps1、scripts/TiebaTaskStateCheck.java；Android驱动只在androidTest。没有新增生产Application/Receiver/权限、常驻服务或普通APK诊断入口。

## 中间失败证据保留

- host-e：am task lock没有唤回已终止任务，未作为App缺陷；改用实际最近任务卡片。
- normal-f：系统过渡时UI树尚未就绪；增加有界新树重试，不读取旧XML。
- normal-g：真实菜单忽略当前帖子选择，确认上述生产缺陷。
- normal-h/i：读取阶段没有独立页面就绪时间，且准备位置依赖引导恢复，不能证明稳定位置缺陷。
- normal-j：实际普通后台已保存3610px，进程恢复也是3610px；前台检查却在onResume之前立即执行，11项1失败。检查改为等待真实前台，不修改生产滚动恢复逻辑。故本轮不声称修复生产位置恢复缺陷。
- explicit-build.txt首次因命令中SentencePiece本地路径拼写错误失败；修正路径后重新成功，不作为生产编译缺陷。

以上日志前缀为 artifacts/local-ai/tieba-process-task-20261026-（跨日保留原前缀）；构建日志使用20261027。

## 已确认验证

- explicit-verified-build.txt：608 JVM，失败/错误/跳过0；Debug与测试包、Debug/Release Lint通过。
- explicit-r8.txt：签名隔离Release混淆构建通过，运行验证仍是Debug目标。
- normal-k：引导11/0、普通前台基线11/0、真实普通进程恢复11/0、清理1/0。实际普通进程4163→4544，后台标记3608px、bodySource第二帖，恢复3608px精确相等；显式选帖、实际正文、原推荐身份及空回退栈正确。

## 尚未验收

本轮是隔离Debug及受控正文，未证明真实来源权限、长帖恢复后的实网评论增量、进程死亡时进行中的网络/预读交接、首页列表进程恢复、ARM全App内存与耗电、本人推荐收益或最终普通升级。整体开发未完成。

## 最终重复与回归

normal-l独立重复：引导11/0、普通基线11/0、普通进程恢复11/0、清理1/0；位置3555px→3555px精确一致。与normal-k合计两轮68项0失败（含引导、基线、恢复和清理，非68个独立用户场景）。最终同生产代码菜单/旋转/有效与弱行为/失败返回17/0、登录读取75/0；日志 tieba-process-task-20261027-final-{tieba-selection-ui,login-read-flow}.txt。结束时指定模拟器隔离App及测试进程均停止，无匹配进程输出。代码修复仍未安装手机或普通包。
