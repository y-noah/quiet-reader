# 文字可见就绪不等待慢图片

## 缺陷与生产修复

统一阅读器原来只在onPageFinished把ReadingEngagement设为ready。图片请求仍在等待时，文字已完成实际可见帧、用户已经阅读及滚动，但阅读计时未开始，无法积累正确内容/平台弱反馈。

新增onPageCommitVisible→postVisualStateCallback，在仍属于当前readerWeb、readerRevision及readerPageUrl且正文确有text块时，调用共用readerReady。保留正文已取得、非登录限制、非不可用、非视频兜底的条件；只有图片占位的文档不会提前ready。原onPageFinished作为完成路径保留，原锚点恢复仍在原位置，没有提前恢复滚动或改动20秒/主动滚动/前台/正文版本校验。没有增加定时器、后台工作、来源脚本或模型推理。

此改动改善可见文字的阅读资格判定，不让图片下载更快，也不能将较早ready冒称网络性能提高。

## 实际WebView反例与回归

SlowImageReadingProbe使用随机数据库和明确合成段落。先由真实统一阅读器加载，再用委托生产WebViewClient的测试包装器提供受控图像InputStream；只有图片响应被闸门阻塞，主文档、生命周期及生产可见回调仍是真实WebView。不访问测试图片域名，不以直接调用生产onPageCommitVisible模拟浏览器信号。

等待真实image request、onPageCommitVisible及compositor visual frame；图片在整个20秒合格阅读期间保持未完成、onPageFinished次数为0。等待生产suppressAutoUntil到期才调用实际交互/滚动钩子，并确认scrolled已生效，20秒后退出，由实际onPause和有界writer写入READ。

artifacts/local-ai中的报告：

| 报告 | 结果 | 解释 |
|---|---|---|
| slow-image-reading-baseline-api36.txt | 6/2 | 图像等待时实际可见帧完成，但reading-ready仍false，READ也未产生。该初版夹具还用了固定1200ms，不能把READ失败单独归因于生产缺陷 |
| slow-image-reading-fixed-api36.txt | 6/1 | 修复后早期ready已通过；READ仍失败，定位为夹具早于1500ms滚动恢复抑制窗口结束 |
| slow-image-reading-qualified-scroll-api36.txt | 7/0 | 改等待真实保护结束并确认实际scrolled，慢图未完成时记录一个READ |
| slow-image-reading-repeat-api36.txt | 7/0 | 同生产实现重复通过 |
| slow-image-only-api36.txt | 4/0 | 真实可见帧有图片占位但无文字，pending image时不提前ready；没有进行20秒纯图片阅读，不宣称覆盖图片最终失败后的全部行为 |
| read-interest-visible-text-api36.txt | 7/0 | 正常阅读仍产生一个带实际来源的READ，进入语义画像，单篇不形成平台倾向，关闭学习撤回 |
| read-interest-stale-visible-text-api36.txt | 4/0 | 页面可读但版本不符，20秒滚动阅读仍不产生新候选READ |

原ReadInterestUiProbe也把固定1200ms改成等待实际滚动恢复保护结束，避免较早可见回调改变夹具时序；没有绕过保护或缩短实际阅读时间。该夹具最终正常/旧版本复核报告为read-interest-visible-text-guard-api36.txt及read-interest-stale-visible-text-guard-api36.txt。

最终上述两份报告分别7/0与4/0，build-visible-text-scroll-guard.txt构建成功，JVM337失败/错误/跳过0，diff检查通过。最后构建仅修改诊断夹具，生产实现与慢图7/0、重复7/0及纯图4/0验证包相同。

## 真实预读观察

live-prefetch-visible-text-api36.txt为5/0，当前公开澎湃文章34324278开始前无匹配正文；正常发现页自动预读准备3112ms，实际卡片打开后文字可见就绪824ms，预读无兴趣事件且没有来源WebView重载。LivePrefetchProbe输出键改为cachedReaderVisibleMs，明确现在表示可见文字ready，不再冒称图片或整页完成。

上一轮2524ms测量基于原onPageFinished且文章身份不同，与本轮824ms不是同条件前后性能比较；本轮没有改变HTML排版/图片获取速度，不能推导出加速幅度。只有模拟器单篇观察，没有真机p95或功耗结论。

## 限制

真实用户推荐命中率、慢图片错误/重试的完整生命周期、跨字号/主题/所有来源、FindX7Ultra峰值/功耗与精确release升级仍需验证。该修复确保可见文字的合格阅读不因慢图片丢失，不能替代上述整体验收。未提交、推送、发版或改品牌。
