# 最新空间检查修复的混淆包复核（2026-10-25）

本轮重新构建当前工作树的本地R8包，补齐最近轻量空间判定修复后的精确安装包证据。无生产代码变更；没有提交、推送、发布、改名或改Logo。内容范围继续以文字流平台的热门文章、问答、帖子与讨论为准，不限于新闻媒体。

## 留档与当前包

覆盖构建前，旧App、测试APK与元数据复制到 artifacts/local-ai/release-before-latest-storage-20261025，逐文件核对SHA256一致。既有release-before-20261025没有修改。

- 当前App：319324971字节，SHA256 6f151f4b3c3bb0e06241e6e1fcdd004452f0225c9b6835db6b35843ad12d47ac。
- 测试APK：1869036字节，SHA256 ddc414a0ef4be34036e39dfd976dc31f253cbd2d349968c9f7621afd354590cf。
- 实际包名 app.quietreader.installprobe，versionCode仍为46；只用于本地隔离验证。
- build-release-latest-storage-20261025.txt：离线构建、R8和签名成功。
- release-latest-storage-installed-match-20261025.json：从emulator-5560实际拉取base.apk，哈希与上述App一致。首次通过Select-Object读取包路径的命令未完成校验，改为完整捕获adb输出后才成功；未将首次尝试算通过。

## 当前结果

所有结果在 artifacts/local-ai/。

- release-latest-storage-upgrade-20261025-api36.txt：14项，0失败。复用持久旧schema3→6夹具，没有重新seed、卸载或清数据；收藏页、反馈筛选/详情、来源开关、阅读metadata、外观、会话设置、合成Cookie保持。此轮是schema6数据再跨APK替换，不是重新执行一次schema3迁移。
- release-latest-storage-models-20261025-api36.txt：21项，0失败。非debuggable、现有品牌/原生入口、模型校验UI、实际512维归一化embedding、reranker有限logit及SentencePiece JNI、独立进程与释放退出。模型已存在，cold=false；准备校验5722ms不是首次安装或手机性能结论。
- release-latest-storage-alignment-20261025.json：6个64位库的全部ELF LOAD段满足16KB对齐，0失败；不代表ARM真机运行验证。
- release-latest-storage-credentials-20261025.json：全部38个ZIP成员、395048602字节解压内容，已知明文密钥格式0命中；不证明任意格式秘密不存在。
- build-store-after-latest-minified-20261025.txt：恢复普通debug/store配置构建成功。当前JVM XML合计419项，失败/错误/跳过均0；任务可能复用最新有效结果，不称为419项全部重新执行。
- 初次直接安装未重签debug包，被Android以签名不匹配拒绝；没有卸载绕过。使用既有scripts/install-diagnostic.ps1按原签名生成并以-r恢复成功，见install-store-after-latest-minified-signed-20261025.txt。失败日志保留于install-store-after-latest-minified-20261025.txt。

## 证据边界与剩余工作

本轮不是完整推荐排序或统一正文阅读的混淆黑盒验收；下一步需要补充实际卡片接受/排序及阅读导航覆盖，不能以模型IPC正常代替完整流程。个人独立留出推荐质量、真实知乎登录全文、真实账号切换、全来源讨论与无障碍覆盖、普通正式包版本递增升级/回滚、Find X7 Ultra整App峰值RAM与耗电仍待验证。整体目标未完成。
