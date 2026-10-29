# 贴吧仅楼内回复新增的续载修复（2026-10-28）

## 原问题与修改

TiebaReplies虽会合并楼内回复，续载调度却只用数值sourcePostId集合识别进展；StructuredSources只给顶层楼层设置该字段。于是来源仍是原楼层、只增加楼内回复时，合并已取得新内容，完成条件和尝试上限却判断“没有新增楼层”，最终失败并丢弃本批结果。

新增真实WebView受控测试，修改前before.txt为4项中1失败/code0：一楼新增一条表情子回复，12898ms后返回“本次未取得新的回复”，没有返回新增内容。不是仅根据代码推测。

生产仅修改TiebaReplies：顶层楼层保留原数值来源ID验证；进展集合另外包含具有有效父楼层和非空section ID的楼内回复。已读窗口判断、稳定窗口完成和尝试上限均使用这个集合；用包含关系判断新增身份，不依赖总数增长。merge仍保留既有楼层身份准入和排序。没有延长20秒期限、改变每批120次/12次等待限制、放宽会话/帖子检查或增加后台任务。解析及关系revision仍为3，因为正文格式与身份生成规则未改变。

## 实测与测试边界

证据目录 `artifacts/local-ai/tieba-child-progress-20261028/`。

- 最终tieba-child-progress.txt：15/0/code -1。实际TiebaReplies异步扫描三批：明确child ID新增、同表情不同child ID、无ID懒加载表情新增，均在没有新增楼层或结束标记时返回；真实ReadingHistory保存/恢复后，懒加载地址切到src不产生新增身份。最后不变窗口必须以“本次未取得新的回复”停止，排除仅因会话失效而退出。
- 初次修复后after-initial-weaker-session-check.txt虽15/0，末项只检查有失败回调，直接decode正文未恢复会话元数据，可能被会话拒绝提前结束。因此该日志不用于证明不变窗口限频。最终测试改成真实save/get/restore并核对具体失败原因，生产没有为测试放宽会话校验。
- tieba-virtual-replies.txt 13/0：30楼虚拟窗口遍历、原帖保留、不重复、账号变更拒绝与close后晚回调抑制。tieba-rehydrate.txt 12/0：70楼缓存恢复穿越旧窗口到80楼、停滞重试。tieba-rehydrate-slow.txt 9/0：延迟1200ms渲染的来源，保留10楼恢复到全部20楼，无跳过。各唯一code -1。
- 698项JVM/107套，失败、错误、跳过0；Debug/Release lint均终态成功。
- 非调试隔离R8真实DynamicReader提取→共同阅读页底部scroll自动续载→pause阅读历史：r8-tieba-child-progress.txt 15/0，两批只有同一原楼层下的新表情子回复，最终3个不同child身份/1个来源楼层及父关系保持。r8-tieba-emoji-identity.txt 13/0，既有含新楼层的懒加载/加载后不重复行为通过。各code -1；未反射生产类或在测试计算生产section ID。

R8实际安装包SHA256 ef3dfe263960636b2a1f4e83d8562fd0fad605266b096233c492a2593e6a5f6c，installed-hash.txt与r8-package.json一致。这些均是受控来源窗口，不证明真实贴吧完整分页/无ID同内容身份准确率或表情网络解码；原有源验证和真实账号门槛继续保留。

## 普通候选与清理

保留升级AVD实际f382143b/schema7基线，未清数据/重种：覆盖前12/0，原签名f43a3db6覆盖后12/0，真实双模型19/0，复验12/0，合计55/0，各唯一code -1。合成历史、收藏、阅读与外观/会话设置、Cookie保留不是用户真实登录权限证明。

普通候选New-20261028-f43a3db6.apk，319466194字节，SHA256 f43a3db6348237f765b3955609884636c81457a4c3bd1f7cd05b45b7461bc0ec；实际安装与安全副本一致。app.quietreader/47/0.4.0-upgrade-probe，DEBUG=false/measurement=false配置来自本次实际构建生成文件。原证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37；五项模型长度/散列、六个64位ELF的16KiB对齐和ZIP对齐通过，限定可识别凭据模式无发现。初次主机审计误把32位ABI也按ELF64断言而退出；修正为明确列出六个32位库不在64位对齐审计范围，未修改APK，不把工具错误当产品错误或32位认证。

verification-summary.json严格检查十份最终Instrumentation日志；原失败和较弱中间证据单列。current-candidate.json已指向f43a3db6，phoneInstalled/published=false；手机仍f0d4dd5a。两AVD正常退出，cleanup.json进程0且ADB只留手机，保留AVD数据；本轮未唤醒或操作手机，无手机临时文件。未推送、未发布。

整体目标仍未完成：真实虎扑/知乎受限内容和评论权限、完整贴吧分页、长期多源在线满额淘汰、连续ARM整App峰值、拔线耗电温升、本人推荐收益、独立去重质量及完整可达性/出站审计继续开放。
