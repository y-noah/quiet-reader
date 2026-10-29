# 当前文字推荐版本的压缩包与跨APK数据保留（2026-10-25）

## 范围与精确包

本轮验证当前生产代码的R8压缩、签名及跨APK替换。使用独立app.quietreader.installprobe，普通app.quietreader与discoveryprobe未卸载或清数据。候选未发布、提交、推送或改品牌。

- 旧独立压缩包：319275647字节，SHA256 5c70b4d6e4e2f24e4ab21b323ae0b9697cf6931c9e04023e23a29a81d2d85408。原APK/测试APK及元数据在artifacts/local-ai/release-before-20261025保留，复制前后哈希一致。
- 当前独立压缩包：319324971字节，SHA256 e3b563becd1fe7b54c143f89febdd15682bae5fd43e884646f8c63ed3f981ec2。
- 最终黑盒测试APK：2499820字节，SHA256 64cfe6d7180be5730345125c56b6ee356c213812909308ba8ce728a75c567628。
- release-installed-match-20261025.json记录设备实际base.apk SHA256与当前留档一致。

两APK versionCode均为46，本轮是不同实际APK的覆盖安装及数据库schema3→6迁移，不是已发行旧版到下一正式版本的升级。不同包名的独立验证不能关闭普通正式包的升级门槛，发布前仍需明确真正前驱、签名、下一版本和回滚兼容策略。

## 新诊断与执行

ReleaseUpgradeProbe仅在独立包名允许运行；使用Android标准SQLite、SharedPreferences、CookieManager和真实UI，不引用生产类、反射压缩后的字段或使用生产诊断旁路。runner支持phase=upgrade-seed/upgrade-verify。seed拒绝已有标记，避免重置已有夹具来制造通过。

先在旧压缩APK的实际schema3数据库中植入合成候选、收藏、内容MORE、平台LESS、来源开关、阅读position/expanded、外观及来源会话偏好，以及.invalid域的持久合成Cookie。旧schema没有source字段，保留为未知；更新后不猜补。Cookie值不写入报告。直接插入夹具不代表真实阅读/反馈学习已执行，验证目标是实际生产迁移和读取。

adb install -r替换当前APK，无卸载、无pm clear。当前生产发现/稍后读页面启动，实际迁移到schema6并显示旧收藏。再从偏好→反馈记录→平台筛选→详情查看保留下来的选择，确认查看不会撤销。重复启动验证保持一致。

## 结果

所有报告位于artifacts/local-ai/。

- build-release-text-current-20261025.txt、build-release-upgrade-feedback-20261025.txt：离线打包/R8/签名通过；第二次仅扩展测试，App APK哈希未改变。
- release-upgrade-seed-20261025-api36.txt：10/0，旧schema3中的合成状态可读。
- release-upgrade-current-20261025-api36.txt：12/0，生产迁移、收藏页和各类数据保留。
- release-upgrade-feedback-20261025-api36.txt及release-upgrade-repeat-20261025-api36.txt：各14/0，进一步覆盖压缩后的反馈筛选/详情及不误撤销。
- release-text-current-models-20261025-api36.txt：21/0，non-debuggable、现有品牌/入口、真实UI离线模型校验、embedding归一化512维、reranker有限logit与SentencePiece JNI、独立进程及释放后退出。已有模型，非首次冷安装；此测试不是完整推荐质量或正文读取验收。
- release-text-current-alignment-20261025.json：6个64位ELF LOAD段16KB对齐，0失败。不能代替ARM/16KB页设备实际执行。
- release-text-current-credentials-20261025.json：对全部ZIP成员流扫描已知明文密钥格式，0命中；不证明任意格式秘密不存在。
- 初次检查调用未配置的python命令，未运行检查；改用明确的bundled Python后得到上面有效结果。没有将缺失报告算通过。
- git diff --check及新增两份release诊断Java空白/冲突标记检查通过。

## 不据此宣称完成的部分

合成Cookie跨安装保留不证明知乎/微博真实账号仍然登录；保存原阅读metadata不证明旧正文在新阅读器恢复到完全相同段落。未验证正式普通包版本递增升级、审批后的回滚、全部源正文/讨论、全量TalkBack、用户个人独立留出、Find X7 Ultra ARM整App内存与耗电。以上仍是完整交付门槛。

验证后恢复普通debug/store runner及模型诊断配置，使用-r保留数据，当前独立压缩包和夹具留作后续复核，不删除共享.tools。普通debug安装日志和JVM结果见build-store-after-minified-upgrade-20261025.txt与install-store-after-minified-upgrade-20261025.txt。