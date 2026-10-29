# 2026-10-29 直接返回登录页的读取回归

## 结果

新增 test-only LoginBackFlowProbe 与 login-back-flow 入口，在 owned emulator-5560/API36 上验证生产 MainActivity.onActivityResult(100, RESULT_CANCELED, null) 及随后 DynamicReader/ArticleSession/Repository/实际阅读页。最终25检查/0失败，instrumentation -1。没有修改生产登录逻辑，没有更换普通候选、手机或发布。

## 覆盖

- 同账号返回：即使存在旧缓存，也真正重新读取来源一次，展示新回答，不靠手动“读取当前页”导入。
- 合成账号cookie变化后返回：真实CookieManager完成写入后，读取使用新的会话作用域；话题URL及原始推荐摘要保持；新正文只缓存于当前会话。
- 延迟回调不重复读取；再次变化账号后，刚取得的正文也不能以旧账号缓存复用。
- 来源主文档受控返回HTTP403：保留生产WebView错误回调，旧账号正文不复活，不制造导入结果，不无限重试；给可操作来源入口，不把它误称为“登录失败/正在登录”。

本测试直接将取消结果送入实际Activity，而不是驱动系统Back手势；没有真实来源登录页面、真实账号或受限内容。来源HTML及403是shouldInterceptRequest返回的受控响应，CookieManager及解析/绘制/缓存链是真实Android实现。不能用这25项关闭知乎/虎扑真实登录验收，也不证明来源全部评论权限。

## 执行与失败保留

首次启动模拟器后只有驱动、目标隔离包未在运行环境中：run-1 返回 instrumentation target missing，未执行检查。未重启模拟器，补装已存档的v21基线后，初版两成功路径run-2为20/0。追加来源拒绝控制后重新构建、lintDebug通过，run-final为25/0。两版驱动及基线APK的散列存于package-hashes.json。未因测试代码变更重复生产JVM/R8或普通升级；前一轮生产源码903JVM及双Lint证据仍按原范围使用。

## 下一步

此次缩小了问题范围：当前受控返回/会话失效/重新取正文未复现故障，继续排查实际来源响应、账号权限和首次超时。真机待用户重新提供，不自动访问。模型去重安全改造仍未完成，当前相关性reranker不得直接作为事件等价分类器。

证据：artifacts/local-ai/login-back-flow-20261029/。普通暂存57644a12、手机805b4ff9均未变。测试完成移除隔离驱动、关闭owned模拟器，不改用户手机数据。
