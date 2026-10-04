# News

个人使用的原生 Android 热搜阅读器，使用系统 WebView，无服务器。

## 当前版本

**0.3.27（versionCode 38）**，安装包 `artifacts/News-0.3.27.apk`。沿用包名 `app.quietreader` 和原签名，可直接覆盖安装。源码快照与验证记录分别为 `artifacts/News-0.3.27-source.zip`、`VERIFICATION-0.3.27.md`。

- 默认进入总榜，最多50条；仅汇总知乎、微博、虎扑、财联社、爱范儿。底栏为「总、知、微、虎、财、爱」，六项等宽。
- 底栏改用系统正规中文字形，统一常规字重与基线，保留原颜色；选中项用短线标记。默认20 dp，随系统字号最多放大至26 dp，以适应48 dp点击区域。
- 其他平台的生产枚举、解析器、请求分支、域名规则和专用界面逻辑已删除。升级通过字符串命名空间清理旧缓存与收藏，保留五平台数据及登录会话。
- 总榜推荐权重为知乎/微博1、虎扑0.7、财联社0.65、爱范儿0.2；各来源最多主导20/20/12/12/8条。重复完整标题跨平台合并。最多3路并发，22秒本轮截止；来源不足显示实际数量，不伪造50条。爱范儿使用最新文章源。
- 榜单缓存超过24小时不参与总榜；界面显示缓存与来源状态。知乎续读保留已读回答，区分登录限制、无新增和来源末页。
- 更多菜单提供刷新、来源/登录及阅读设置。支持深浅色、阅读字号与图片预览。

## 边界

仅展示当前会话能够获取的内容，不绕过登录、验证码或付费限制。自动测试中的合成缓存和会话内分页不代表真实账号认证成功。系统字体可能随手机厂商略有不同。Android 8.0起支持；当前实测范围见版本验证记录。

## 开发与验证

```powershell
./scripts/bootstrap.ps1
./scripts/build.ps1
./scripts/start-emulator.ps1
./scripts/test-local.ps1
./scripts/release.ps1
./scripts/test-release.ps1
```

工具在 `.tools`，签名密码由 Windows DPAPI 保护，不进入仓库或源码包。构建和当前验证脚本可用 `-GradleInit` 指定本地初始化脚本（`build.ps1` 通过 `-Tasks '-I',路径,任务`）。

`test-current.ps1` 支持五源迁移/导航、动态读取、知乎分页及阅读位置旅程。`test-release.ps1` 验证实际压缩签名包。均只操作专用模拟器，不操作手机。旧平台诊断工具已移除；历史文档记录历史版本，不代表当前支持功能。

## 隐私与依赖

只有 INTERNET 和 ACCESS_NETWORK_STATE 普通权限，无分析/广告 SDK，不导出 Cookie，不同步账号，Android备份排除应用数据。

- jsoup 1.23.2，MIT；许可随包提供。
- Android NIO desugaring 2.1.5，GPLv2 with Classpath Exception；许可随包提供。
- 更多图标来自 Google Material Icons（Apache-2.0）。底栏使用系统字体，不额外打包字体资源。