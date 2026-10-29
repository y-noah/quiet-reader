# 首页连通无障碍控件回归（2026-10-27）

本轮只修改Android测试驱动：ReleaseReadingFlowProbe的appointment-event阶段增加UiAutomation实际控件树、按钮角色、focusable、可见至少48dp区域、无障碍焦点及标准点击动作检查。没有修改正式App代码，普通候选仍New-20261027-2bf60a5d.apk，未装手机或发布。

隔离R8 App仍为7016946a255b46aacd5ad0d29956bbd91a6a207242a689daeb81a822a96cbd6a。测试使用公开任命标题两条及测试缓存，非真实账号与个人质量验证。证据目录`artifacts/local-ai/appointment-evidence-20261027/`，日志前缀a11y-。

## 测试条件修正

首次a11y-standard-raw.txt共26项、4失败，四项均为ACTION_ACCESSIBILITY_FOCUS；名称/角色/48dp/标准点击及来源拆分通过。测试当时没有启用触摸探索，Android不接受该模式外的无障碍焦点请求。保留失败，不以它认定生产焦点故障。

驱动保存UiAutomation原ServiceInfo，临时请求FLAG_REQUEST_TOUCH_EXPLORATION_MODE，等待AccessibilityManager确认实际启用后才测焦点，finally恢复原ServiceInfo。最终在真正触摸探索模式下，每一焦点动作均被接受，没有移除或弱化焦点断言。只是条件修正，不是TalkBack语音引擎测试。

## 本轮终态

- 标准1080×2400/420dpi/字号1.0：27项、失败0，INSTRUMENTATION_CODE:-1。
- 临时640×1280/320dpi，即320dp宽，字号1.5：27项、失败0，INSTRUMENTATION_CODE:-1。
- 两组本轮成功项合计54，首轮26项失败运行不算通过，不与先前75项重复计数。
- 刷新、设置、返回、具体话题选项名称准确，暴露标准Button角色、可聚焦，真实可见点击区至少48dp，无障碍焦点接受；设置及返回通过连通ACTION_CLICK实际切页，话题选项同法打开菜单。
- 原始两个平台标题在来源选择器可发现，实际菜单拆分为两卡，原快照冻结，原来源及热度保留，没有虚构内容/平台偏好。
- 显示尺寸、density及font_scale均保存观察结果，结束恢复1080×2400/420dpi/1.0；临时测试服务配置恢复。结束关闭App及AVD，见a11y-cleanup.json。

## 限制

仅覆盖上述首页与往返/菜单动作。没有验证TalkBack朗读顺序、完整手势导航、全部设置页及阅读页，未做人耳听读或所有字号/主题逐页视觉验收。显示小屏配置与节点可达证据不能当作所有正文没有裁切的证明。真实账号、贴吧楼层、ARM内存/能耗、独立去重及个性化收益仍待验证。
