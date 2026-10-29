# 当前R8真实文章与断网复读（2026-10-26）

本轮仅增加ReleaseLiveReaderProbe、release-live-bbc-20261026.json断言资产、实际繁体原文解析回归及runner入口，没有修改生产源码。证据目录artifacts/local-ai/minified-live-reader-20261026，严格汇总见summary.json。

## 方法

emulator-5560/app.quietreader.discoveryprobe，实际非debuggable的现有R8包，公开Android API观察，不反射混淆生产类、不改keep规则。通过实际MainActivity的discovery_item/discovery_candidate普通阅读Intent打开，不绕过Repository/原生BBC解析/正文缓存/统一渲染；不是从首页卡片触摸或模型ITEM回调开始的测试。

匿名抓取五篇官方繁体文章，原始HTML及SHA保留在artifacts/source-research/overseas-chinese-20261026/release-reader-trad-*.html。独立遍历原始Next内容的paragraph节点，保留52/70/42/37/24个段落，共225段；只作为断言输入，绝不写入App正文缓存。derive-release-reader.py核对原HTML哈希后裁剪身份/metadata/content派生JVM样本，release-trad-manifest.json记录来源及派生哈希。本轮JVM94XML/567测试、0失败/错误/跳过；新增一个用例逐段核对五篇繁体原文。

冷读选择当前Cookie会话下不存在磁盘正文且没有阅读历史的文章，不删除/清空/改名/变更元数据伪造未命中。检查实际DOM全部段落含尾段，以及新落盘的原生bbc-article节身份、精确URL、完整段落和获取时间。图片检查指向滚入视口的第一张.part图片，必须该图naturalWidth>0，不能以其他图片代替。

断网轮先终止App，保存模拟器Wi-Fi/移动数据原开关，关闭两者；探针另检查无activeNetwork，随后重新启动实际阅读。核对相同真实正文的时间戳/inode、DOM全文和首图。主机finally恢复原网络并核对一致。截图已实际查看，分别为R8-reader-final-cold.png和R8-reader-offline.png（位于安全的overseas-source-review目录）。

## 最终严格驱动结果

| 日志 | 范围 | 检查通过/失败 | 段落 | DOM观察 |
| --- | --- | --- | --- | --- |
| target-video-cover | 已获取的ckq6nvg7ny1yo/trad文字报道，指定视频封面复核 | 11/0 | 42/42 | 见日志 |
| target-cold | 此前未缓存的cwg7xy2mjnl0o/trad实际下载 | 12/0 | 37/37 | 4462ms |
| target-offline | 终止进程后的同文断网恢复 | 14/0 | 37/37 | 1146ms |

最终驱动37项检查通过；指定首图/视频封面解码宽800，无横向溢出，无video播放器。时间包含不同网络/缓存负载，仅为测试观察，不是呈现帧或性能补丁提速证明。离线证明限于该正文与已缓存首图：同文其他未预读配图确实未解码（width=0），不能表述为整篇所有图片已完整离线。

实际安装App哈希与package.json对应App一致，R8映射归档；精确App/驱动SHA见summary.json。全部命令会话终态，隔离App/驱动停止后ps匹配0，网络恢复Wi-Fi=1/mobileData=1。无手机、普通包升级、提交、推送或发版。

## 原失败与证据修正

- cold.txt：70段真实正文和新落盘身份通过，但图片断言失败。image-diagnostic.txt/body-diagnostic.txt后续测得空DOM；失败现场截图实际显示完整正文及配图。原观察没有临时启用已被生产关闭的WebView脚本，evaluateJavascript取得空值。仅修正探针启用/恢复脚本，final-existing-article.txt复核70段和真实800像素图片11/0；保留原失败，不宣称生产图片修复。
- final-cold.txt：另一篇尚未缓存的42段文章确实进入来源协助页，未取得正文，不算成功。保存原截图和失败。该篇已保存原HTML在新增JVM解析回归通过，同篇final-cold-retry.txt随后新获取全文成功；这不能证明首次远端失败是哪个网络/响应原因，也不是永久稳定性证明。
- final-cold-retry.txt旧图片断言只要求任意图解码，虽然12/0，但截图可见指定视频封面当时仍空白。该图片结论降级，不作为指定封面通过。最终驱动改为首图targetWidth，target-video-cover.txt实际确认指定封面800像素；其他图片部分仍未加载，不扩张为全部图片覆盖。

## 未关闭项

本轮补齐特定真实BBC文章在当前隔离R8的获取/统一阅读/离线首图证据，不是完整海外来源长期稳定性。尚未验证精确R8模型计算中的真实网络阅读切换、真实账号全文与账号变化、本人推荐收益、Find X7 Ultra整App峰值/非充电耗电/发热、最终普通包全路径升级交付；原9.9秒因果仍未证明。整体开发保持未完成。
