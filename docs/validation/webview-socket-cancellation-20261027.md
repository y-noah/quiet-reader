# Chromium来源流与实际HOME释放验证（2026-10-27）

## 本轮范围

此前RequestQueue真实TLS18项证明Android HttpsURLConnection读取可取消，但没有创建WebView。本轮WebViewSocketCancellationProbe让Chromium自身建立真实本地HTTPS连接，服务端持续保留未结束的HTML正文流，等待页面提交后再触发两种正式释放路径：SourceSurface.release，以及MainActivity真实HOME/onStop关闭持有来源WebView的AnswerStream。

服务端观察连接EOF/关闭，而非shouldInterceptRequest返回的伪响应或Loader.cancel计数。正文未完成且对端仍连接时才触发取消；两种路径都检查source surface移除，HOME路径另检查owner清除、原已缓存阅读正文保持。

本地TLS仅使用测试APK内公开测试证书。测试WebViewClient只有在SslError的URL精确等于本轮127.0.0.1临时端口地址、证书DER精确等于夹具证书时才继续；其他证书/URL取消，前后清理WebView SSL决定。未更改正式WebViewClient、全局SSL默认设置、NSC、URL规则或正式Manifest。证书初始化提取为共用LoopbackTestTls，原SocketCancellationProbe复用并重跑。

## 当前证据

目录 `artifacts/local-ai/webview-socket-cancellation-20261027/`：

- API36/host GPU隔离模拟器，WebView真实流10/0，最终成功码；直接释放后观察关闭6ms，HOME/等待onStop后观察关闭617ms。后者包含HOME命令和生命周期调度，不是onStop方法耗时，也非手机耗电证据。
- 同驱动共用证书初始化后的RequestQueue真实TLS18/0；既有AnswerStream后台受控DOM/活动续读10/0，均最终成功码。本轮选定38项/0，不加到此前重复运行的统计中宣称新增覆盖数。
- 本轮仅增加/调整androidTest代码，没有新增正式代码，因此保留上一轮正式646JVM/Lint证据，未重复执行。新的debug与测试驱动散列见hashes.json，当前普通手机候选仍e4dae6c6，不因测试代码变化重新生成普通包。
- 测试执行器及TLS服务端退出，隔离App和AVD停止，进程核对见cleanup.json；未读取或安装手机，未推送/发布。

## 不能据此声称的范围

使用本地合成页面与注入的AnswerStream来源，不通过真实知乎页面身份/登录获取路径，且不证明所有站点JS/ServiceWorker、握手/DNS、重定向、媒体和系统共享Chromium进程均停止。来源测试WebViewClient只提供严格本地证书决定，正式来源安全规则保持原样。这里证明正常已建立且传输中的Chromium正文流可沿当前释放路径退出，不能标记全部真实平台网络审计完成。

真实虎扑/知乎账号、贴吧楼层/换帖、独立去重质量/个人兴趣收益、Find X7 Ultra整App峰值≤1GB和非充电耗电/温升继续开放。已询问下一轮手机可用时间，回答到达前不枚举或操作手机；独立电脑开发可继续。
