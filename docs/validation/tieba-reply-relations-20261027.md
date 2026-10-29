# 贴吧作者、回复对象和楼内回复（2026-10-27）

用户确认截图来自贴吧。顶部“回复 · 名字”实际是作者，容易误解为目标；正文“回复 + 个人主页链接 + 冒号”被拆成多个大段；楼内回复虽抽成独立section，却未保存父楼层或显示层级。现代虚拟窗口合并还仅接收有楼层post ID的section，可能丢失新增楼内回复。

## 修正与边界

通用评论标题直接显示作者，回答身份不变。贴吧只有开头明确为“回复 + 贴吧 /home/main 个人主页链接 + 中英文冒号”才提取目标，放入紧凑小字；正文提及、外链、缺少分隔或普通评论均不猜目标，保留后续正文和表情。

根据.lzl_single_post/.pb-lzl-item实际归属记录楼内身份、父section ID；不按前一评论/相同昵称猜父子。无父证据时只显示已知楼内身份。楼内缩进14 CSS px并有左边线，作者/目标元信息11 CSS px，沿用字号缩放。关系字段进入历史序列化、磁盘异步拷贝、指纹和大小预算；窗口合并按父楼层插入新子回复并去重，数量预算含子回复。旧快照缺字段不猜层级，重新获取来源才得到完整关系。

## 验证

4项新增JVM覆盖拆段前缀归元信息且正文/表情保留、无效目标不修改、窗口合并/去重/历史保持关系与后续楼层顺序、桌面实际父归属且无对象保持空。旧ReaderHtmlTest更新两处作者显示预期，保留折叠/回答导航断言。

最终100 XML / 624 JVM项，0失败/错误/跳过；Debug/测试包、Debug及Release Lint、普通签名R8通过。首次沙箱编译在JDK关闭本地依赖JAR时AccessDenied，重试仍失败；工具批准本地离线构建权限后成功。首轮真实单元失败为旧文案断言，更新后完整回归通过，失败日志保留。

仅指定emulator-5560 / quiet-reader-api36隔离应用：浅色22/0、深色22/0，含19→28字号、无横向溢出、表情、磁盘/历史恢复、作者显示及目标小字/缩进；虚拟续读/账号变化13/0、实际阅读视图自动续读21/0，共78/0。截图人工复核，安全目录 C:/Users/10197/Documents/Codex/review-assets/20261027-reply-relations，light-modern.png及dark-modern.png。源DOM为受控合成，表情为既有官方PNG夹具，不是用户真实帖DOM复现。未访问手机，结束已停止隔离应用。

证据前缀 artifacts/local-ai/reply-relations-20261027-，包括unit-summary.json、final-build.txt、light.txt、dark.txt、tieba-virtual-replies.txt、tieba-virtual-ui.txt、release-build.txt、candidate.json、apk-check.json、credential-scan.json、signature.txt。

## 普通候选与开放项

新候选 C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-f3008963.apk，SHA256 f30089630dc94d33a6f27474eb05caadee1a7b7c1384fbaee332cf93ce4d195c，app.quietreader，47 / 0.4.0-upgrade-probe，319449810字节；原普通签名验证通过。模型两份清单及5文件精确大小/哈希、所查ELF16KB前提、常见凭据扫描通过。DEX核对新包含nestedReply、parentSectionId、replyToAuthor、nested-reply，旧42b包均无，未复用旧包冒充修复版。

未安装、上传或发版，手机仍718f6e1f。精确普通包运行/保数据升级、真实贴吧楼层目标/登录楼内回复/翻页恢复仍待复核；本人推荐收益、整体ARM资源与长期耗电等总验收仍开放。
