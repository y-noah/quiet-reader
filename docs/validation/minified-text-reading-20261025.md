# 最新文字阅读代码的R8联通验证

日期：2026-10-25。覆盖近期贴吧帖子身份、知乎补充去重/部分提示、连续回答预算等生产改动所在构建的R8兼容性；本次黑盒流程未单独执行全部来源边界，不替代对应debug探针或最终普通发行包。

## 构建与受测身份

先将旧release-discovery-flow的App/Test/metadata复制至独立archive-flow目录，再执行scripts/models/build-release-probe.ps1 -Offline -FlowInstall，构建成功。原签名覆盖安装至API36/emulator-5560，无卸载/清数据。隔离应用app.quietreader.flowprobe确认为non-debuggable。

App319341343字节，SHA256 9f7c87277ff66893e820c9d571b8240a4b9ebc222498276856f5d7b4c1e69923；Test1930570字节，SHA256 16dbaf9325db7758e6eefb52ddb62b03f38b292875f28dabff4ddbd96f8678556。完整身份见release-discovery-flow-package.json；minified-text-reading-installed-20261025.json确认设备实际base.apk与本地App散列一致。

## 结果与范围

ReleaseReadingFlowProbe实际25项/0失败：通过原生界面准备本地模型；真实向量和重排缓存、结果待接收且列表冻结、确认后更新；收藏、稍后读、统一阅读展开并显示完整合成缓存正文、滚动与返回；内容少看包含实际平台，反馈后的新顺序仍需确认；配对评价盲选/未知标签持久化，评价不污染活动列表或学习反馈。

候选和正文为明确合成数据，不以开发者兴趣或六候选全进入前20的盲评计数证明用户推荐收益。原始日志artifacts/local-ai/minified-text-reading-latest-*.txt。截图已取回安全审阅目录quiet-reader-review/minified-text-reader-20261025.png并查看，当前浅色展开正文换行和段距正常、顶部控件无重叠；不能扩展为全部来源/主题/字号视觉验收。

check_apk：64位两架构共6个ELF LOAD对齐通过，模型307948507字节，failures为空。明文凭证格式扫描38个ZIP成员、395069906解压字节，findings为空。分别见minified-text-reading-binary-20261025.json、minified-text-reading-scan-20261025.json；不代表完整安全审计或ARM运行/功耗证据。

随后恢复普通debug/store诊断构建并原签名安装。有效JVM447/0，本次构建复用未变测试结果；新接口预算探针18/0的独立证据见zhihu-answer-budget-20261025.md。未提交、推送、发版或改品牌。

## 仍待完成

独立事件/时间的真实个人评价，实际登录权限和完整知乎内容，Find X7 Ultra整App峰值RAM与功耗/发热，以及普通最终版本的精确升级、失败/回滚验收仍开放。当前隔离压缩包及上述合成流程不能关闭这些门槛。
