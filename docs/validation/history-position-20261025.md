# 跨进程语义阅读位置 / 2026-10-25

## 实际基线

新ReaderHistoryProcessProbe两次独立instrumentation运行，中间实际am force-stop，检查前后Linux PID确实不同。合成24段正文，字号17时位于第8段约35%，真实Home触发onPause保存历史；新进程字号25恢复同一历史内容。旧代码只有像素位置5852.95，恢复到第3段约79.69%。基线history-position-prepare-baseline-api36.txt为3项1失败（无语义记录），history-position-restore-baseline-api36.txt为6项1失败（段落错误）。正文读取未失败，问题是位置语义。

## 实现与边界

SavedReadingViewport保存schema版本、原文URL、取得时元数据hash、section id、单个块type/value的SHA-256，以及有界ReadingViewport几何记录。不会额外持久化段落文本/原始Cookie或账户token；记录最大4KiB、URL最长2048字符。恢复需URL、元数据身份相同，并在相同section内找到唯一相同块；前面插入段落时可映射至新block索引。目标变更/缺失、重复内容歧义、无效几何、未知schema或记录过大则不使用该语义标记，保留既有像素回退。正文读取、Cookie/namespace/有效期、候选版本检查保持原路径，标记本身不赋予访问权限。

ReadingHistory新增可选viewport字段，旧save签名和旧记录继续可用；clearBodies保留位置/展开集合及语义marker。MainActivity只保存与当前页/当前原生scrollY相符的去抖采样，顶部继续保持顶部；最近滚动还未得到有效采样时不把旧锚点盖成新位置，回退像素。正常open先加载marker，实际收到并校验当前正文后才匹配；已匹配的语义位置沿用可见帧恢复，不依赖旧字号的像素。未完成正文在途转屏也携带尚待匹配的marker，不持有旧Activity。

保存仍发生于既有rememberReading/onPause等路径；这不是持续磁盘日志，不承诺在未执行生命周期保存的强杀/崩溃中还原最后一瞬间。唯一匹配只表示相同文字/图片块，在已显示的当前权限正文中定位，不证明整个事件正文未更新。图片/内联链接元数据不单独进入块hash；目标为读过的段落内容位置。语义marker不含旧正文，正文清理后仍可保留；访问内容的会话有效性继续由当前取得流程负责。

## 最终证据

artifacts/local-ai/：

- build-history-position-final.txt：构建成功，JVM **403项，失败/错误0**，比本轮前397增加6项。覆盖JSON往返及无正文文本持久化、前插段落移动索引、目标歧义拒绝、URL/元数据/正文变化拒绝、未知/损坏记录、缺失section/非渲染块。
- history-position-prepare-handoff-api36.txt：3/0；history-position-restore-handoff-api36.txt：6/0。字号17→25仍为同段约34.98%，目标像素改为12724.57，而不是原5852.95。
- history-position-compact-prepare-final-api36.txt：3/0；history-position-compact-restore-final-api36.txt：6/0。字号25→17恢复同段约34.97%。
- history-position-refresh-prepare-final-api36.txt：3/0；history-position-refresh-restore-final-api36.txt：8/0。生产ReadingHistory.clearBodies后marker保留，准备当前会话的新合成正文并在前方插一段；目标从block8移动至block9，恢复约34.96%。清理的是历史正文API，不能声称覆盖完整App缓存清理流程。为不改变其他历史，诊断备份并恢复非本次合成条目。

进程、SharedPreferences、生产save/open/restore、WebView排版都真实执行，正文均为缓存或重新准备的合成数据，没有实际请求公开来源，也不证明真实登录内容或现实平台结构稳定性。诊断保存并恢复MainActivity字号偏好，清理唯一合成阅读历史与临时测试设置。

## 最终受影响回归

同一最终安装包，artifacts/local-ai/：

| 报告 | 结果 |
|---|---|
| reader-rotation-history-position-final-api36.txt | 15/0 |
| reader-image-flow-history-position-final-api36.txt | 14/0 |
| reader-image-ownership-history-position-final-api36.txt | 14/0 |
| login-read-flow-history-position-final-api36.txt | 61/0 |
| image-cache-clear-history-position-final-api36.txt | 7/0 |

保留各Probe既有解释，受控登录不是平台真实账号验收；清理回归证明原位置/保存/反馈/登录保留规则未退化，不据此声称全来源位置迁移已测。git diff --check通过。

## 未完成

更多真实来源与图片重排、异常退出最后一瞬间的保存、完整辅助功能、真实账号/长期新鲜度、个人推荐独立留出、Find X7 Ultra整App资源/温度/耗电、精确签名升级和回滚仍未整体验收。没有提交、推送、发版、改名或改Logo。
