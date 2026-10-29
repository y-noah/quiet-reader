# 圆点始终回到首页顶部

用户要求：无论是否有刷新/更新，点击圆点都回到最上面。仅此行为获准实施；随后关于推荐式换批的讨论不构成推荐策略修改授权。

后续用户明确说“允许你上面的修改”，已授权按讨论分步实施推荐式更新，蓝本见 ../requirements/recommendation-refresh-v1.md。上述段落记录当时边界，不代表后续仍未授权。

DiscoveryActivity 将圆点与下拉刷新入口分开。圆点先返回feed、清理待恢复锚点并将首页与Controller记忆位置置顶，然后沿用现有刷新/接受更新逻辑。加载中圆点保持可点击，用于返回顶部；refreshFeed保留loading门控，不重启请求。下拉刷新逻辑保持现状。

新增 UpdateDotTopProbe：真实Activity、ListView、圆点、Controller和SQLite快照，受控来源Transport，不使用用户账号/网络。验证无待更新时回顶部且启动原刷新、加载中回顶部不重启、有待更新时异步接收后仍在顶部、设置页圆点返回首页顶部。

证据目录 artifacts/local-ai/update-dot-top-20261028/：

- after.txt、repeat.txt各13/0/code-1。
- explicit-feed-cache-action.txt 6/0/code-1，保持有限缓存额度、自然到期和后台拒绝。
- feed-navigation.txt 18/0/code-1，下拉刷新与导航隐藏规则通过。
- build-final.txt编译成功；同构建JVM结果107 suites/701 tests、0失败/错误/跳过。
- first.txt、diagnostic.txt保留测试前置条件失败：初版要求setSelectionFromTop(12,-30)后首个可见位置至少12，实际因顶部padding/偏移仍有第11行可见，列表已离开顶部。仅测试改为真正的“前面还有可滚动内容”；不是产品修复失败或被抹去的结果。

本次没有更新普通APK候选、没有手机访问、没有发布。本修改与早期阅读续页修复均还需R8/普通升级核验；手机保留c315361c。
