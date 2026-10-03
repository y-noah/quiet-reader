# News 0.3.25 — 总榜、续读与豆瓣修复

版本 0.3.25 / versionCode 36，包名 `app.quietreader`。本次仅处理启动页、回答续读、豆瓣话题及主页导航，并回归相关阅读行为。

## 修改

- 冷启动默认总榜；旋转和设置返回仍恢复当前页面。旧版「什么值得买」页面状态回退到总榜。
- 底栏六等分，顺序为总榜、知乎、微博、虎扑、财联社、爱范儿。什么值得买从独立导航及聚合来源移除，聚合来源数改为12；旧存储和正文链接兼容保留。
- 知乎来源网页未追加时，通过来源 WebView 中同源 XHR 请求同一问题的回答分页，Cookie 不导出。保留真实 next 游标，按回答 ID 合并去重，最多3页/次、20秒/次。外域、不同问题及非HTTPS游标拒绝。关闭页面和旧批次迟到回调不修改当前内容。
- 续读成功、没有新增、要求登录、来源可见末页分别显示结果，失败保留已读回答；底部也显示结果并提供来源登录入口。展开/收起与追加同时发生时保留已经取得的回答，不丢页。
- 豆瓣热榜中的 `search_term` 不再被拼成不存在的话题链接；`group/topic` 帖子保留在话题列表中。话题接口使用作用域匹配的本机豆瓣会话。旧豆瓣榜单缓存升级，其他平台缓存及账号数据保留。
- 豆瓣空结果、全视频无图文、接口拒绝访问直接给出明确结果、重试及来源入口，不落入无用的动态等待。不可用结果不缓存，避免重试再次拿到旧失败。

## 验证证据

专用 API35 模拟器 `quiet-reader-test / emulator-5556`，没有操作物理手机，没有清除应用账号数据。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| JVM 单元测试 | 228项，0失败/0错误 | `app/build/test-results/testDebugUnitTest/` |
| Debug/Release 构建及 Lint | 构建通过；Lint 0错误、18项警告 | `artifacts/repaired-build.txt`、`artifacts/release-build.txt` |
| 生产 WebView XHR 分页 | 33项通过；重复首批后取得新回答、续页、末页、403、非法游标、3页上限、超时、退出取消 | `artifacts/initial-paging.txt`、`artifacts/round2-paging.txt` |
| 原有动态读取回归 | 5个旅程/36项通过；同数量变化、渐增正文、无新增、登录保留 | `artifacts/dynamic-regression.txt` |
| 使用者阅读回归 | 加强后39项通过；第二次新启动重复验证 | `artifacts/anchor-journey.txt`、`artifacts/round2-journey.txt` |
| 正式签名混淆包 | 覆盖安装成功，36项真实界面检查通过 | `artifacts/release-branding.txt`、`artifacts/release-branding-evidence/` |
| 签名一致性 | 与本机原 News 0.3.24 同一证书 | `artifacts/signature-comparison.txt` |

分页测试拦截合成网络响应，但使用真实 WebView XHR、生产解析器、合并和回调，不替代生产回调。使用者回归使用合成文档验证真实 Activity/WebView/点击及呈现；这些结果不等于真实账号认证成功。

长文测试实际展开第1回答并滚动到非零位置。追加前后首个可见段落 ID 相同，段落相对阅读区域的 top 从36.75变为36.869 CSS像素；滚动值500变为677是对顶部说明高度的补偿。顶部原生状态变为两行时阅读区域整体下移45设备像素，未发生正文换段或内容丢失。截图在 `artifacts/anchor-journey-evidence/` 和 `artifacts/round2-journey-evidence/`。

## 实网观察与边界

`artifacts/live-reading.txt` 使用生产 Repository/DynamicReader/AnswerStream：当前豆瓣榜单19个有效话题中，6个返回18–20个图文入口，其余13个返回登录限制；知乎当前首条热榜读到3个回答，续读时来源显示登录限制，仍保留3条。电脑直连请求也观察到豆瓣 `403 / need_login`，不是把这些拒绝请求算作成功读取。

本轮没有真实账号登录后的续读认证，也未重测API26或用户手机。不能保证所有来源、网络、账号和话题均可读；需要登录、验证码、付费或权限的内容仍受平台限制。本轮修复包括正确使用已有会话和明确结束失败状态，不绕过平台访问控制。

## 两轮独立复查

用户要求先修复发现的问题，再完成两轮检查。最初复查发现全视频空话题被缓存、来源恢复入口缺失，已修复；同时加强原本可能0→0的滚动测试。该次未通过记录保留，未算入通过轮次。

重新初验通过后，由独立「测试者」与「使用者」执行两轮复查，报告保留在 `artifacts/tester-round1-passed.md`、`artifacts/user-round1-passed.md`、`artifacts/tester-round2.md`、`artifacts/user-round2.md`。复查限于本次需求和相关回归，不把未认证账号状态计为已通过。

第二轮分页首次执行时模拟器进程退出，没有结果，未计通过；重启专用模拟器并重新安装测试包后完整重跑，33项通过。没有因此修改生产代码或降低断言标准。

## 交付

- `artifacts/News-0.3.25.apk`：183110字节，同原签名，可覆盖安装，不要先卸载。
- SHA256：`B6ED6814572E0118092A4BB57BF268A7C896602924CAE838B473A21CC18D99C9`。
- 证书SHA256：`ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`。
- `artifacts/News-0.3.25-source.zip`：本次提交的源码，不含工具、签名、密码和设备数据。

本机Java直接下载Maven依赖遇到TLS连接问题，构建使用临时本地代理经Python标准HTTPS从原Google/Maven Central下载，未修改项目仓库源和依赖版本，也没有关闭远端TLS证书验证。该构建辅助文件仅保留于忽略目录 `artifacts/`。
