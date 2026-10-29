# 全页面文字布局与阅读返回控件修复（2026-10-28）

## 发现与修改

本轮新增隔离R8 `reading-layout` 探针，覆盖首页、设置、阅读偏好、缓存与存储、高级与诊断、来源顶部/底部、正文折叠/展开九个页面。仅在隔离包种入六条合成文字和缓存正文；未替换普通App或手机数据。截图及原始日志位于 `artifacts/local-ai/full-page-layout-20261028/`。

首次测试编译缺少CountDownLatch限定名，已修正，原build.log保留。首轮深浅色各11项/9失败：整行字体度量误判符号按钮，展开后的立即DOM读取遇到导航返回null。改为实际字形墨迹/基线检查，并有界等待正文DOM，保留原失败截图与日志；不是用取消断言掩盖实际裁切。

同一旧App字节复测深浅色各13项/2失败，两个真实失败均为阅读页“返回”字形底部超出可用高度（85px > 76px）。MainActivity.frame仅清除该48dp控件的垂直padding，保留水平10dp、单行、随系统字号增加的宽度及48dp触控高度。未改全局按钮或导航行为。

## 实际证据

| 配置 | 结果 | 图像 |
|---|---|---|
| 320dp宽/200%系统字号/28号正文/深色 | 14/0，code -1 | fixed/dark九张 |
| 同上/浅色 | 14/0，code -1 | fixed/light九张 |
| 原1080×2400/420dpi/100%系统字号/28号正文/深色 | 14/0，code -1 | standard九张 |

实际目视两张极限配置九宫格及普通配置九宫格，共27张页面截图；另目视修改前后返回控件放大裁剪。返回底部笔画恢复，本轮未观察到文字重叠或正文文档横向溢出。极限配置正文clientWidth/scrollWidth均284px，普通配置均376px，折叠/展开的实际DOM正文锚点及展开状态正确。折叠省略与滚动边界的局部可见卡片为预期行为。

自动原生文字审计只检查完整可见且非主动省略的TextView，使用实际绘制字形界限；不代表所有样式/语言/平台都已验收。DOM检查覆盖合成文字正文的文档宽度及展开，不证明所有图片、表格、评论和视频均适配。

修复后的隔离App SHA256 `086545a2c86c3f0253bd0cc43447a492890298cfd6b31f720ab2de6e5c0d53af`，319461349字节；驱动SHA256 `4eda43e3982cbe1632875e7152e5f57cad1fa553dd00247dee7b6d87270603c3`。fixed/installed-hash.json记录实际拉取base.apk匹配。debug构建及679项JVM（失败/错误/跳过均0）、Debug/Release lint与R8构建均终态成功，日志和jvm-summary.json保留。

## 恢复及边界

探针finally恢复原外观和原正文大小（包括原无键状态）。模拟器显示恢复为测试前1080×2400/420dpi/fontScale1.0，before/restored记录一致。精确删除本轮导出截图，force-stop测试包并关闭quiet-reader-api36；cleanup.json记录匹配进程0、ADB已无emulator-5554，保留userdata。没有操作手机、推送或发布。

普通签名候选另行覆盖升级验证，不能把隔离包作为手机安全候选。真实账号、贴吧完整楼层/短表情、ARM全场景≤1GB、拔线耗电/温升、个人推荐收益、独立去重质量和完整TalkBack/全部平台布局等验收仍开放。

## 普通原签名候选及保数据覆盖

同一修复另行编译普通候选 `New-20261028-4ce61dc6.apk`，SHA256 `4ce61dc6abe4761b37ffe2c0ae653790644b74cc90a8fdaa70bb0a73fa98037b`，319449810字节。包app.quietreader/47/0.4.0-upgrade-probe，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。原签名SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37；模型清单与五个文件、六个64位ELF及ZIP对齐验证通过，全ZIP可识别明文凭据扫描无发现。静态扫描不等同运行时隐私审计。

保留quiet-reader-upgrade-api36的770c98c3状态，未种新夹具/清数据/卸载：覆盖前12项、4ce覆盖后12项、实际双模型与释放19项、再复核12项，四份日志共55项0失败；本轮使用am instrument -w输出流，未单独归档最终instrumentation code，不能追称各code -1。checks与失败字段及进程终态已核对。合成Cookie/保存条目/阅读配置/迁移schema7保持，不代表真实登录验收。当前新普通包未另跑九页面布局探针，布局三轮证据属于上述隔离R8字节，不混称普通包完整页面覆盖。

ordinary/installed-hash.json记录实际拉取base.apk与新构建匹配，安全目录副本再次匹配，current-candidate.json更新4ce61dc6；phoneInstalled=false、published=false，手机仍f0d4dd5a。关闭升级AVD并核对匹配进程0及ADB无emulator-5562，保留userdata；未操作手机或发布。
