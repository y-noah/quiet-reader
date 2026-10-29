# 贴吧真实楼层、楼内回复与自动续载 — 2026-10-28

整夜授权连续会话，证据仍归档 artifacts/local-ai/phone-night-20261027/。普通 Find X7 Ultra / PHY110 / Android16，App f0d4dd5a 保持不变；本轮只改临时测试驱动，未发布、推送或清除主App数据。

## 实际路径与结果

ReleasePhoneSourceProbe 的 phone-tieba 从 Tieba 阅读历史挑选已有楼内回复的公开帖 https://tieba.baidu.com/p/11139792603 ，只检查相关来源记录，不导出其他来源历史、Cookie、昵称或回复正文。来源共享 WebView 显示现代 PC DOM、无 loginGuard，截取最多60行；本轮51行（14楼层、37楼内回复）。两次独立页面加载可能得到不同虚拟窗口，不能按回复顺序或相同短字串猜身份。

实际打开普通阅读页、省略 discovery_candidate 避免人为 READ/QUICK_EXIT 标签，通过产品菜单重新读取；明确选 quiet-reader.invalid/?render 排版页，日志 firstIsReader=false / chosenReaderOrigin=true。等待正常自动续载抑制窗口结束后，仅 window.scrollTo(bottom)，由普通 scroll 回调触发加载，未调用私有 loadMore。阅读段落10→30，含22条楼内回复。Home 正常生命周期触发 onPause/rememberReading，读取同URL且 at/bodyAt 新鲜、段数不少于30的 reading-history 快照，关系版本2。

最终 tieba-history.txt：47项/0失败。命令 am instrument -w 使用格式化 stream，主机进程终态 exit0；没有保存原始 INSTRUMENTATION_CODE，不能声称原始 code -1。tieba-summary.json逐项核对原日志与清理记录。

逐条验证7个显式顶层floor ID保持顶层、parent为空；13条≥12字符且来源/保存正文唯一、父楼层存在的楼内回复，nestedReply=true且parentSectionId与来源父floor的哈希标识一致，replyToAuthor只来自显式“回复+用户链接+冒号”。5条短或歧义回复跳过、18条来源父楼不在阅读窗口跳过、可比范围内0条未找到。没有把跳过的23条算作正确。真实样式同时检查嵌套标记≤14px、缩进≥10px及无横向溢出。

本轮证明加载窗口内可比关系和一次自动续载；不证明全部楼层/分页、纯表情或短重复回复的唯一身份、登录专属权限、直接正文磁盘缓存或全平台表现。窗口不同不能用51行对30段推导丢失数量。

## 保留的失败与测试纠正

- tieba-source-compare、tieba-unique-relation、tieba-floor-ids 初次10/1：两个独立来源窗口不同，来源14个父floor ID而阅读初始只有1个floor/8子回复。用两字符正文跨窗口匹配会误指另一回复。现仅父floor在保存窗口且长文本双边唯一时才允许无child ID匹配；有显式child ID仍按身份严格验证。不能把旧失败定性为已复现产品父子错配。
- tieba-window-continuation-build 首次 Android 编译不支持 Files.readString；改 readAllBytes 后 final-build 成功。
- tieba-window-continuation 9/1 已证明10→30续载，但测试误用BBC raw-cookie缓存文件推导。Tieba使用规范化账号身份，文件名不符；不是已证实的产品缓存写入失败。没有在测试重新实现Cookie规范化，改走真实Home生命周期快照，范围明确为reading-history。
- tieba-history-build 最终成功。普通App hash f0d4dd5a82ac7510a2f7652f06707bdae10821333c3d7918abb2e44e98f01167，临时驱动87c9a70fe49be7d5e7c79e0af3914aa89206bafb598526a008900c1fa579c20d，手机实际驱动 sha256sum匹配。没有修改生产解析代码来迎合测试。

## 清理和剩余门槛

驱动卸载Success且包查询为空；指定 new-night-tieba.xml 和旧诊断图片/差异文件精确清理，stay_on_while_plugged_in恢复0，返回New首页后息屏，电源Dozing。主App和账号数据保留。

真实虎扑/知乎受限正文和评论权限、正常Controller全App高负载连续峰值≤1GiB、拔线日常耗电温升、长期实际缓存轮换、个人真实推荐收益和独立去重质量仍开放。短时真实模型完整归属样本727.53MiB不是全场景峰值认证。
