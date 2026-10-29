# 阅读记录与预缓存正文选择（2026-10-28）

## 缺陷与改动

MainActivity原先先选阅读历史，再校验是否能用于当前话题；只在没有历史正文时读取RAM。两种问题：有效但较旧的历史遮盖较新的预缓存正文；不符合当前元数据的历史被拒绝后，没有重新考虑有效RAM，直接进入磁盘/来源路径。

ArticleVersion.preferredCached分别调用现有可用性校验，选择获取时间较新的有效正文。时间相同保留阅读历史，因为历史可能含有用户已展开的后续页面。MainActivity仍保留原阅读位置、展开集合和语义锚点；选中RAM时走renderCached，不重复保存该命中。强制重新读取仍跳过两类缓存。未延长在线10分钟/离线7天期限，未改变登录身份、来源版本和贴吧选帖校验。

## 证据

目录：`artifacts/local-ai/reader-cache-choice-20261028/`。

- before.log/before.xml：先把原来的“有历史就先用历史，再校验”逻辑提取到选择函数，5项中2项失败，分别对应上述两个选择缺陷。它是原逻辑的确定性单元复现，不冒称改动前完整页面实测。
- after.xml、after-build.log、jvm-summary.json：新增5项全部通过，全量698项/107套，失败、错误、跳过均0；隔离Debug与Android驱动编译成功。
- android1.txt：首次实际页面20项中3个DOM断言失败。文档对象/无来源获取/展开集合均已通过，但测试在WebView异步加载前立即取样。后续仅将DOM断言改为8秒期限内等待实际目标文字，未改变生产代码。原始失败保留。
- android2.txt、android3.txt：上述等待修正后各20/0/code -1。四种真实Activity场景：较新RAM、历史元数据不匹配、相同获取时间、RAM较旧。实际reading对象/正文文字、dynamic与pendingArticle为空、保存的展开状态均核对。
- 最终驱动将数据库名改为生产支持的`test-ui-*.db`格式，并等待语言记录线程收尾后删测试数据库；final-build.log、android-final1.txt、android-final2.txt记录重编译及两轮20/0/code -1。之前驱动数据库名前缀不符合诊断白名单，会落到隔离App默认数据库；不将之前运行用作反馈隔离证明。所有运行均在discoveryprobe模拟器包，未操作手机账号/数据。
- lint.log：正确原生源码路径下Debug/Release静态检查终态成功。该检查不代替R8实际阅读链路。

## 压缩包与普通升级续验

ReleaseReaderCacheChoiceProbe只使用Android框架、DOM、历史JSON及捕获公开BBC响应，不反射生产类，也不从测试类生成生产Document。每个场景先实际原生解析一次文章，退出取得历史，再仅修改持久历史的正文标记/获取时间/元数据；RAM仍为真实获取的原始正文。四场景覆盖较新RAM、历史元数据不匹配、相同时间历史、较新历史。

- r8/first.txt：首次第7项期望手工加入磁盘的展开标记出现，失败；App实际还有同进程ReadingMark，优先保留该实际标记。测试改为核对首次真实阅读形成的非空展开集合，而非要求磁盘修改覆盖内存标记。生产代码未改。
- r8/final1.txt：首次“正文实际获取一次且历史存在”合并断言失败；当时没有拆分计数与历史状态诊断，不能精确断言失败原因。final2.txt同驱动四场景26/0。随后驱动增加Activity.isDestroyed销毁屏障与分开的请求数/持久正文诊断，未放宽生产选择条件。
- r8/lifecycle1.txt、lifecycle2.txt：最终驱动两轮各26/0/code -1，四场景各一次初始原生获取、重开无第二次原生文章获取，实际DOM正文选择及真实展开状态保留。非debuggable目标与ranchu隔离包也被断言。该证据不能区分所有磁盘恢复路径，也不作公网/账号/图片传输审计；非文章HTTPS刻意不可用。
- r8/installed-hash.txt与package-lifecycle.json一致：安装包SHA256 c8b5262bdd59ea759fead6be2834e4a93ddbcfce3d11df663a54cf8a260f5989。最终驱动SHA256见同JSON；r8-lifecycle-build.log终态成功。此构建没有重新安装到手机。
- 普通保留升级AVD从5830ab3c/schema7基线开始，不清数据：upgrade-before12/0，覆盖f382143b后upgrade-after12/0，normal/models19/0，upgrade-repeat12/0，合计55项成功，各code -1。合成收藏、字体/外观、阅读位置/展开、迁移状态与持久Cookie保留；不是用户真实平台权限证明。
- normal/installed-hash.txt与normal/package.json、package-audit.json及本地安全副本一致。原签名SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37；16KiB对齐、5项模型长度/散列和限定已知凭据模式检查通过。config-build.log/config.txt是普通构建同参数重新生成的DEBUG=false/measurement=false配置证据，不冒称从压缩DEX恢复未保留常量。

当前本地候选New-20261028-f382143b.apk，319466194字节，SHA256 f382143b493d0ac9b424829e5bea93977febee6823a59362b1ea1f3ef98c5c40。current-candidate.json已更新；phoneInstalled/published仍false，手机仍f0d4dd5a。两AVD正常退出，cleanup-release.json确认相关进程0，保留AVD数据供后续覆盖验证。

## 最终范围与剩余项

此处验证合成正文经真实Repository、ReadingHistory、MainActivity、WebView的选择与显示；没有注入来源网络，也不是公网更新/真实评论权限验收。没有新增后台循环或请求。仅比较已通过现有校验的历史与RAM，不宣称解决所有磁盘版本争用或历史标记在任意正文变更后的恢复。

本分项已进入普通签名候选并完成上述R8专项；手机未动、未推送、未发布。保留既有完整目标：真实虎扑/知乎受限登录内容、贴吧完整分页、长期多源缓存与实际淘汰、ARM连续整App峰值、拔线耗电温升、本人推荐收益与独立去重质量、全页面/出站审计等均未关闭。
