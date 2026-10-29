# 阅读展开的辅助功能/触摸对照（2026-10-25）

当前辅助功能问题保持未通过。原探针在从未展开时也可能把折叠状态检查计为通过；现已要求先证明展开，再检查收起，避免假阳性。增加真实触摸对照，只作为诊断，绝不替代辅助功能验收。节点选择改为先遍历子节点，并记录节点文本、描述、操作列表及接受结果。

## 已完成实验

- reader-node-diagnostic-light-20261025.txt：只匹配最深text节点，9项/4失败。WebView链接名称实际放在contentDescription、text为空，不能只依赖text选择。真实触摸后DOM aria-expanded=true、preview消失且原生expanded含正确section ID。
- reader-node-description-light-20261025.txt：恢复匹配描述，移除辅助功能焦点移动，9项/2失败。ACTION_CLICK accepted=true，但原生与DOM均未展开；触摸对照展开成功。收起ACTION_CLICK也未生效。此实验不支持把问题归因于父节点选择或焦点移动。
- reader-js-state-light-20261025.txt：仅在测试中保持DOM采样的JavaScript启用，9项/2失败，表现相同。已恢复原有采样后关闭脚本；生产配置未更改。不能把启用JavaScript当修复。
- reader-normal-control-light-20261025.txt：普通中文标题/作者/提示，保留长回答和相同操作目标，320dp/150%，9项/3失败。触摸展开成功，辅助功能点击仍失败。说明问题并非仅限极端长作者；本记录修正此前只按极端作者描述的范围。

上述场景字体/主题、48CSSpx操作目标与无水平溢出检查通过，但不是全页面视觉或完整TalkBack证明。每个日志保留真实失败数。辅助功能问题的根因仍未确定。

ReaderTypographyProbe提供normalText对照，FeedStoreInstrumentation通过target=normal选择，scripts/test-discovery-layout.ps1增加-NormalText并区分日志文件名。首次控制夹具构建曾试图修改final label而失败，已改为构造时传入；reader-normal-control-build-final-20261025.txt成功。当前JVM XML为466项、0失败、0错误、0跳过。测试只使用项目API36模拟器；未再次安装手机、提交、推送或发版。

## 输入焦点对照

reader-input-focus-light-20261025.txt：普通中文、320dp/150%，输入焦点ACTION_FOCUS与ACTION_CLICK均返回true，但未展开，9项/3失败。触摸展开成功。已撤回无效的输入焦点额外操作，保留标准节点点击。此实验不支持把缺少输入焦点当根因。所有构建/安装/仪器进程均已返回终态，无需继续等待旧会话。

## 最小链接与环境边界

新增最小链接控制：同一真实WebView替换为没有阅读器结构、CSP、折叠状态及aria-expanded的普通HTML，仅含一个https链接，通过独立WebViewClient统计导航。reader-minimal-control-light-20261025.txt中ACTION_CLICK接受但导航计数0，总10项/3失败。此结果不能由阅读器toggle处理或长作者单独解释。

reader-screenreader-normal-light-20261025.txt增加UiAutomation的FLAG_REQUEST_TOUCH_EXPLORATION_MODE，结束恢复原服务flags；最小链接仍未触发，整体10项/4失败。请求触摸探索模式不等同完整TalkBack验收，不能称已真实朗读测试。

为排除DOM采样和既有WebView状态，reader-fresh-control-light-20261025.txt用全新WebView（未启用脚本、未采样DOM）挂载到同一个页面，单独加载普通HTML；测试后移除并destroy，再恢复原阅读View。新链接ACTION_CLICK同样接受但导航0，总10项/3失败。该环境提供者为com.google.android.webview 133.0.6943.137，API36。原生设置导航已有实际动作通过证据，但尚未证明WebView所有无障碍动作或不同提供者表现。

结论边界：目前证明的是本模拟器WebView辅助功能链接操作失败，且可在没有阅读器代码的最小控制中复现。尚未证明根因是提供者版本、系统/自动化配置或其他因素；也没有证据证明用户手机发生相同故障。保持A09/U02未通过，下一步应优先环境/独立辅助功能交叉验证，不据此盲改正常的正文、登录或触摸处理。此前“普通作者同样复现”依然成立，但只限上述测试环境。

## 后续：测试环境修正已验证

reader-bound-accessibility-service-20261025.md以真实绑定测试服务和不抑制服务的UiAutomation完成最小控制3/0、极端浅色12/0、极端深色12/0、默认普通文本12/0。旧AutomationOnly同实现2/2仍被拒绝，说明此前不可按阅读器生产缺陷直接推断。原失败记录保留，展开/收起局部探针缺口已关闭；完整TalkBack与手机范围仍开放。
