# 实际模型到未缓存真实文章阅读（2026-10-26）

证据目录：artifacts/local-ai/live-model-reading-20261026，严格结果见summary.json。本轮只扩展ModelReadingInterruptionProbe及测试入口，没有修改生产调度。

## 范围

生产DiscoveryController.canPrefetch要求recommendation为空，beginRecommendation检查preloader.busy，排序前暂停图片预读。因此不绕过保护强行制造模型与推测下载并行。本轮验证实际模型ITEM回调后，调用普通DiscoveryActivity.open，终止模型，再获取之前没有新鲜缓存的真实文章并进入统一阅读器。

使用100条真实公开标题及明确的开发者MORE样本，不代表用户兴趣。实际Repository榜单选择尚未缓存的非视频文章，不删除缓存伪造未命中，不生成合成正文。首页跳过无关抓取并显式请求推荐，所以本轮不是普通自主调度验收。回调触发打开也不是触摸或呈现帧基准。

## 结果

| 日志 | 检查通过/失败 | 正文来源与块数 | 客户端关闭 | Binder退出 | 阅读器恢复 | 正文DOM观察 |
| --- | --- | --- | --- | --- | --- | --- |
| embedding-thepaper | 11/0 | 澎湃，179 | 17ms | 132ms | 145ms | 2061ms |
| rerank-bbc | 11/0 | BBC，32 | 68ms | 233ms | 600ms | 4690ms |
| final-embedding-thepaper | 12/0 | 澎湃，7 | 11ms | 124ms | 150ms | 1818ms |
| final-rerank-bbc | 12/0 | BBC，45 | 56ms | 215ms | 504ms | 4308ms |
| cached-regression | 9/0 | 原有合成缓存夹具 | 10ms | 128ms | 192ms | 412ms |

最终驱动33项通过，含历史运行共55项通过。两轮最终真实文章检查确认打开/加载不产生隐式兴趣，反馈仍只有开发者预置MORE。正文时间戳不早于打开，DOM包含实际正文，无动态回退或残留待加载请求；lateResults均为0，模型进程退出后阅读检查时仍不存在。不同文章及网络条件下的时间不能解释为性能提升；DOM观察不等于全部图片完成或呈现帧。

JVM为94份XML、566测试、0失败/错误/跳过。安装App与归档SHA一致：c56033689f1eaca208046ce3d87ed77e391a4f666c418e5ca0d81bda13651315；最终驱动SHA：5296936079bb820ca768108ba971d99bcff98a6d14d498314b8b2ae5566d4740。

仅emulator-5560与隔离discoveryprobe调试包。命令均结束，隔离App/驱动已停止，进程匹配0。真实正文缓存与历史保留；原合成模式仅清理其自身夹具。未操作手机、普通包、提交、推送或发版。

## 未关闭门槛

不证明精确最终R8自主/真实网络路径、原9.9秒等待根因、ARM峰值内存/耗电/发热、真实账号权限或独立个人推荐收益。原有整体交付门槛继续开放。
