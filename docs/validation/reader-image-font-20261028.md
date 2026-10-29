# 图片字号重排与暂停后续读

状态：本地修复与回归，不发版、不推送、不访问手机。手机与公开候选仍c315361c；本轮普通包待下文最终复验补记。不能据此关闭全部贴吧分页、真实账号、ARM资源或耗电门槛。

## 真实失败

目录 artifacts/local-ai/reader-image-font-20261028/。

新建 ReaderImageFontContinuationProbe，使用普通MainActivity、V2EX格式缓存正文、当前scope实际24px PNG、真实字号菜单与系统触摸，DiscussionStream.Loader仅替换传输计数，不改页面位置、保护截止时间或私有续读调用。

- reader-image-font-round1.txt：4/0。实际readerRevision1→2，图片分支确实重排，真实滑动在保护期内抵达续读阈值，随后仅调用一次Loader。
- reader-image-font-background-round1.txt：4项1失败，进入设置后仍发生一次Loader调用，原失败保留。
- background-diagnostic.txt：5项1失败，明确LEAVE callsBefore=0；157545ms时readingResumed=false、pending=false、calls=0；157793ms Loader调用时resumed=false、focused=false。已证明是真实暂停后的新工作，不能解释为仅测试等待或离开前已启动。

## 修复

原有onPause已取消手势安排的检查，但WebView重新布局导致的迟到滚动也会进入maybeLoadMore，后者只看wasStopped。Activity在onPause与onStop之间可能仍被迟到回调触发。

增加统一readingVisible条件：已resume、有窗口焦点、未stop、未finish/destroy。maybeLoadMore和最终loadMore入口都要求它成立。前者防自动迟到回调，后者覆盖旧页面动作等其他入口。不增加定时器、后台任务、缓存额度或模型工作。

## Debug复验

所有下列成功日志均有唯一INSTRUMENTATION_CODE: -1；shell exit0不作为通过证据。

- 图片前台fixed/repeat两轮各4/0，实际Loader回报resumed=true/focused=true。
- 图片转设置fixed/repeat两轮各5/0，实际onPause后pending=false且Loader始终0。
- 原纯文字早期手势3/0、长页无手势2/0、进入设置5/0、阅读预读21/0、贴吧楼内进展15/0。
- build-fixed.txt：完整764 JVM/112 suites，0失败/错误/跳过；lint.txt：Debug/Release成功。

## R8图片场景

ReleaseTiebaIdentityProbe增加图片字号模式，无生产私有类/字段反射。使用实际贴吧解析、父子楼层合并、真实字号菜单和触摸；仅将夹具emoji URL的WebView响应替换为固定24px PNG并委托其余生产WebViewClient回调。这验证实际图片解码/字号重排，不证明远端图片会话或图片缓存获取。

- r8-image-font-round1.txt 5项1失败：16段夹具一次滑动尚距底1783px，未达到0.75×2051px阈值，不作续读结论。只将图片模式夹具缩为12段，生产触发距离与手势不改。
- r8-image-font-round2.txt与r8-image-font-repeat.txt各16/0。字号动作至手势结束693/710ms，实际文档URL重载、24px PNG解码、两轮自动回复续载、不同懒表情不丢失/不重复、父子关系和历史持久化均通过。
- R8此专项证明前台图片路径；暂停后的实际Loader0证据来自上述Debug受控传输，不扩大成混淆后台传输或全部能耗证明。

## 未关闭

本人推荐收益、ARM全App峰值、拔线能耗与温升、长期完整在线缓存和受限账号权限仍开放。本轮不操作手机、不更换候选指针；原早期手势报告中“未做R8”限于当时历史状态，本次对图片路径补充上述范围证据。

## 普通候选与最终核对

普通原签名保留userdata升级：upgrade-before.txt12/0、upgrade-after.txt12/0、normal-models.txt19/0、upgrade-repeat.txt12/0，唯一成功code均为-1。包含既有schema9、旧阅读位置/设置/收藏与合成持久Cookie，且实际双模型/JNI运行和退出通过；合成Cookie不等于真实平台登录权限。

仅本地归档 normal/app.apk，SHA256 c9694addb065896b92d437652b205961beb838575e0be6fe7ace5d2e61e5b45c，319466194字节。installed-hash.json确认实际安装base.apk相同。R8专项包及两驱动散列见package-hashes.json。terminal-summary.json逐项核对最终成功日志，原三个失败日志保留。未更新手机或current-candidate，未推送/发版。
