# 后台资源源码复核与残留文案 — 2026-10-27

本轮只做源码检查与三处文案修正，未访问手机或启动模拟器。生产代码/res全文检查不再含“稍后读”，release Java编译成功。证据artifacts/local-ai/removed-later-copy-20261027/compile.log和summary.json。改为“已重置偏好”“阅读缓存已清理，阅读进度保留”“接近历史参考内容”，不删除兼容旧数据或改变偏好计算。未为文字替换新增测试。

## 静态检查的范围

DiscoveryActivity.onStop取消启动阅读、暂停试用评估、调用controller.stop。DiscoveryController.stop清除动作预读预算和过期/供给回调，暂停正文与图片预读，取消修复和推荐，取消来源采集。ReaderPrefetch.pause/trim清除预算并取消空间检查与投机任务；ArticlePreloader.pause清队列、取消请求和DynamicReader；ImagePreloader.pause撤销策略监听和超时，取消在途请求。HotSourceCollector.cancel清理队列并作废任务/来源修订。

RankingClient.close发送RELEASE并解绑；RankingService处理RELEASE、解绑、客户端死亡与低内存时终止当前推理及线程池，100ms后退出独立ranking进程，空闲8秒退役。RequestQueue使用可超时的线程池，取消时断连交由取消线程，关闭后终止线程池。RecommendationPolicy监听仅附属于活跃工作，低电/省电/温升/灭屏时限制，恢复不自行开新工作。Manifest未声明启动广播、JobService、前台服务权限或唤醒锁。

以上是对当前代码路径的静态核对，不是全App无耗电或退后台零内存的证明。系统可以保留缓存进程/WebView；磁盘落盘、断连和模型退役不承诺瞬时完成。真实ARM同时峰值≤1GiB和拔线能耗/温升仍待测，不从代码检查推断合格。

当前普通待测39bffda4仍未包括本轮三处文案；未重新生成包/推送/发布。下次源码变更打包时一并纳入，避免只为文字重跑全部模型回归。真实来源权限/贴吧关系/推荐收益等项目门槛仍开放。
