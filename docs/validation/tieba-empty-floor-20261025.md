# 贴吧空首楼与回复身份验证（2026-10-25）

## 问题与修复

统一文字阅读要求主帖、作者和回复身份独立。StructuredSources.tiebaPost 原来使用成功提取的section数 index 判断第一段是否为主帖。首楼为空或只有不能提取文字的视频时，index保持0，后面普通回复被标成正文，并将byline从楼主覆盖为回复作者。

现在独立记录原DOM有效楼层位置position；index仍只统计已提取段落，保留100段可见内容界限。空楼不使后续回复变成正文，也不占可见段落预算。来源提供的post_no及现代DOM回复区识别仍优先参与判断，不改变视频政策、登录、缓存键、模型或分页协议。

## 验证与保留失败

全部报告位于artifacts/local-ai/。

- 修复前新增两个回归用例：414项/2失败；tieba-empty-floor-baseline.xml和build-tieba-empty-floor-baseline.txt保留。分别复现署名被替换、空楼后的文字被标成正文。
- 修复后完整JVM：414项/0失败/0错误，build-tieba-empty-floor-final.txt与build-tieba-empty-floor-ui-verified.txt。覆盖空/视频首楼以及大量空楼后仍有可见回复。
- 既有实际阅读/讨论动作回归：discussion-flow-tieba-floor-final-api36.txt，57/0。
- 新增实际Activity/WebView缓存阅读边界：首轮discussion-flow-tieba-floor-ui-api36.txt因诊断反射引用不存在的dynamicReader字段导致测试进程崩溃，不算通过。将诊断改用既有dynamic所有者，未改变生产要求。
- 最终discussion-flow-tieba-floor-ui-verified-api36.txt：59/0。空视频首楼之后只有一张讨论卡、回复作者正确、原楼主署名和视频兜底标记保留、不重载来源；同时原三平台连续分页、登录/缓存保护、去重和位置回归通过。这里的空首楼是合成边界，不声称当前真实帖子出现过此情况。
- 真实匿名贴吧现场live-tieba-text-review-api36.txt：当前热榜话题21段/40块，跟随到真实帖子14段/35块，均进入统一缓存阅读页且sourceReload=false。来源话题与原帖范围明确区分。修复后live-tieba-text-floor-final-api36.txt再次取得同一当前热门话题21段/40块及帖子14段/35块，readable=1/1、probeErrors=0；live-tieba-text-floor-final.png已目视核对标题、原作者和统一正文排版，无本次视口下遮挡。两次是同一话题，不等于多帖覆盖。本轮未验证真实账号登录、楼中楼、虚拟列表全部加载或真实多页续读。
- git diff --check通过；因三份Java文件未跟踪，另检查其尾空白及冲突标记，0处。

## 交付边界

此修复改善文字平台的正文/讨论归属，不表示全部来源适配或个人推荐已完成。Find X7 Ultra全进程内存/功耗、真实账号与个人留出反馈、精确签名升级继续待验收。未提交、推送、发布或改名换标。