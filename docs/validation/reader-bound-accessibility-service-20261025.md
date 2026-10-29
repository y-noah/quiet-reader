# WebView辅助功能测试环境修正（2026-10-25）

## 问题和依据

此前API36/WebView133.0.6943.137中，ACTION_CLICK返回true但阅读器、全新WebView普通链接均未触发操作；普通复选框还缺少checkable属性。它不是已证明的生产阅读器逻辑缺陷。

Android16 AccessibilityManagerService.getEnabledAccessibilityServiceList在UiAutomation抑制服务时返回空列表。Chromium133 AccessibilityState从该列表推导辅助功能模式，WebContentsAccessibilityImpl接受点击仅表示向后续实现分派，不保证业务动作完成。核对的官方源码：

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java
- https://chromium.googlesource.com/chromium/src/+/133.0.6943.137/ui/accessibility/android/java/src/org/chromium/ui/accessibility/AccessibilityState.java
- https://chromium.googlesource.com/chromium/src/+/133.0.6943.137/content/public/android/java/src/org/chromium/content/browser/accessibility/WebContentsAccessibilityImpl.java

## 实现边界

仅测试源码新增ReaderProbeAccessibilityService；测试Manifest/XML最终限制在src/discoveryStoreTest，仅discoveryStoreUi配置引用；它是空事件处理服务，不朗读、不记录事件、不联网、不执行手势。脚本仅允许现有项目模拟器，临时启用服务；ReaderTypographyProbe使用FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES保留真实服务可见性，并等待绑定、验证系统列表含测试服务。自动化服务flags、模拟器原有服务启用项和总开关在finally恢复。常规reader-layout模式默认使用正确环境，-AutomationOnly保留故障对照。失败仍按真实检查数拒绝，触摸对照不代替辅助功能通过。

普通App Manifest不含该测试服务；reader-bound-service-package-check-20261025.json记录构建APK检查（这是构建产物hash，不冒充已安装签名APK的hash）。未向用户手机增加任何服务，未更改生产JavaScript配置、登录、导航或触摸处理。

## 证据

- reader-bound-service-control-20261025.txt：实际服务可见、全新普通链接触发1次导航、复选框false→true，3/0。
- reader-bound-service-extreme-light-20261025.txt：320dp/150%、阅读字号28、极端长标题/作者，12/0；真实ACTION_CLICK展开和收起均更新DOM aria-expanded、预览状态；没有触摸fallback。
- reader-bound-service-extreme-dark-20261025.txt：同条件深色12/0，同样没有触摸fallback。
- reader-service-default-normal-light-20261025.txt：更新后的默认脚本、普通中文作者12/0；RESTORED accessibility_services=True accessibility_enabled=True。
- reader-service-default-automation-baseline-20261025.txt：同一最终测试实现的AutomationOnly故障对照，可见真实服务数0、链接导航0、复选框checkable缺失，2/2，被脚本正确拒绝，不当成回归通过。
- reader-service-final-build-20261025.txt：构建成功；JVM仍466/0。
- 事后独立读取：enabled_accessibility_services=null、accessibility_enabled=0、font_scale=1.0、无尺寸/密度override，物理1080x2400/420dpi。

## 验收结论

关闭的是本环境下阅读展开/收起ACTION_CLICK探针失败与其错误测试配置。不是修好了一个已证实的手机缺陷，也不是完整TalkBack朗读/顺序/所有页面验收。测试服务不是TalkBack，没有人工听读证据。A09/U02其他范围、真实登录全文、个人推荐效果、手机长会话内存/耗电/热、空间提示来源仍开放。最终普通发行精确包和后续设置/阅读样式的真机验证也未完成。未提交、推送、发版、改名或更换logo。

## 服务组件范围最终收紧

releaseDiscoveryUi驱动也已不声明测试服务，见settings-reader-minified-20261025.md。对新source-set配置重新构建store测试包后，reader-store-scoped-service-control-20261025.txt默认最小控制3/0，服务和总开关RESTORED均True；说明服务只在需要的模拟器配置可用。当前手机验证驱动和App均无该声明，未向手机安装。
