# 正文失败图片收紧与阅读位置（2026-10-26）

## 问题与实现

此前正文图片按原尺寸比例或未知尺寸240px预留空间。生产图片请求失败或返回非法图片字节后虽返回404，预留盒仍存在，真实BBC阅读画面可见大块空白。本轮保留初始尺寸避免正常图片加载跳动，但对已经完成且naturalWidth为0的正文配图改成紧凑的“图片未加载 · 查看大图”入口；原有大图打开及重试操作继续可用。

ReaderImageState只操作本App逃逸正文中的figure > a > img，排除行内表情和视频封面。限定当前正文页面，参数以JSONObject.quote转义。页面完成及原生子资源失败通知经现有readerScript临时执行固定脚本，结束后恢复禁用脚本，CSP不放宽。仅处理已经完成且解码宽度为0的图片，即使收到同URL错误通知也不收起已解码或仍等待的图片。收起前后按可见阅读段落差值补偿滚动位置，不改变阅读学习条件、图片网络/缓存归属或模型调度。

## 当前精确隔离包验证

artifacts/local-ai/reader-image-failure-20261026保存全部日志。FeedStoreInstrumentation以mode=reader-image-failure运行，使用生产Repository图片路径及受控HTTPS响应：503、200非法PNG、正常PNG和延迟成功PNG；驱动主动将四图设为eager只用于覆盖，不构成主动预读效果证据。

- baseline.txt为8检查/1失败，失败项正是错误图片没有收紧。
- fixed.txt为首版8/0；随后增加完成状态保护，不能用首版代替最终版。
- guarded.txt为最终10/0：两种失败收紧、可见段落位置保持、成功图片不收起、延迟图片不误报且稍后正常解码；实际触摸入口、大图Retry恢复PNG及Close保留原WebView；原生DOM脚本计数归零且JS恢复关闭。另两项主动输入同URL通知，分别覆盖已成功/仍等待状态；这是受控陈旧通知逻辑检查，不声称复现真实网络竞态。
- 同一最终debug包的guarded-reader-image-flow/ownership/session分别14/0、14/0、13/0。共51项最终检查；不是51种实际平台账号场景。
- guarded-compact.png已人工查看：两个失败入口紧凑，两个有效合成PNG正常显示。它是合成布局证据，不标作BBC实网图片恢复画面。
- verified-package.json记录实际debug App SHA256 09d6b95e9b0b1407969d4b51c0b745e8aec50e2989a170c4ccc2bdfa01f5e9ca，与拉取installed-base.apk一致。测试包也归档。两个隔离包已停止，ps未匹配进程。

incorrect-mode-store.txt曾误用phase参数而运行其他存储检查；incorrect-folded-fixture.txt曾因合成节标题及版本缺失没有生成图片。这些不是图片修复证据，原日志保留。最终故障测试依赖反射，不能转述成混淆R8运行验证。

## 独立R8回调验证

新增ReleaseImageFailureProbe及phase=image-failure，仅用Android API、序列化正文缓存和受控HTTPS响应，不引用生产类、不反射、不直接执行ReaderImageState。实际非debuggable隔离发行包通过正常Intent恢复合成正文，503/非法PNG经生产图片路径触发原生回调；最终r8.txt为8/0：双失败紧凑收起、段落位置保持、正常图片不收起、慢图等待及稍后解码、原生工作后脚本关闭。未重做大图Retry，不用这8项替代debug的真实触摸覆盖。

测试DOM查询会临时打开脚本；释放错误后先等待1000ms，不在错误回调期间轮询脚本状态。恢复脚本的断言在全部回调及查询结束600ms后进行，不声称覆盖所有脚本竞态。正文由驱动写入当前私有缓存格式，故不是实际SSPAI解析/网络正文或账号内容证据。四图显式eager仍仅为测试覆盖。

verified-r8-package.json、App/test、映射及installed-r8-base.apk归档。App SHA256 1299076a6701ad12319d10807cff755227f9ff51de23a5c3809afebf6c14e341与安装字节一致，r8-compact.png已人工查看，两个隔离包停止后进程匹配0。首轮build-r8.txt因新增驱动CookieManager引用歧义及Android无Files.writeString编译失败，修正测试代码后build-r8-fixed.txt成功；未改生产修复或旧普通候选。一次Get-CimInstance进程观察被拒绝，随后使用实际构建终态、包清单及安装验证，不将该观察错误当构建失败。

## 普通候选与未完成门槛

最新普通本地候选New-20261026-0f5fbf62.apk，SHA256 0f5fbf6282058d9d3c9dad30e930eb9643b4130460fcc36722012a075dc81a18，319432842字节；包含此前BBC接入、外观、短标题去重及本轮图片修复。app.quietreader/47/0.4.0-upgrade-probe/显示名New，v2签名验证及原证书一致，非debuggable，仅INTERNET/ACCESS_NETWORK_STATE，普通构建测量开关false已另存。未安装或发版。

最终lintRelease通过（不声称零警告），95份XML/573单元测试0失败/错误/跳过，git diff --check通过。六个64位ELF LOAD段16KB对齐及全部80 ZIP成员常见明文密钥格式扫描通过，模型字节307948507；这不是ARM16KB运行或任意秘密不存在的证明。普通候选的精确升级/运行、真实账号全文、个人推荐收益及ARM全App峰值/非充电耗电/长期温升仍未验收。整体目标保持开放。
