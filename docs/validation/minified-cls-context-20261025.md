# 当前摘要修复后的压缩包功能链（2026-10-25）

## 精确对象

本地隔离包 app.quietreader.flowprobe，non-debuggable、R8压缩，内置已批准双模型。App SHA256 `d7ad8f93910ff02d62ac63fb1a0a9f3dc4f357a45c5d4be96d9cce03662ede1a`，319357727字节；实际安装base.apk散列一致。默认普通包版本、名称及Logo未修改，未提交/推送/发布。

此前包已保留于 artifacts/local-ai/archive-before-cls-context-20261025，避免新构建覆盖原证据。当前包manifest/hash见release-discovery-flow-package.json。

## 真实执行结果

API36现有模拟器运行 reading-flow，39项通过、0失败。该流程使用合成中文候选和缓存正文，但调用当前压缩生产控制器、本地模型和UI：模型准备、排序更新确认、稍后读、统一正文展开/滚动/返回、短阅读不误记学习、真实来源少看反馈、盲化评价、准备后新候选接纳及重复拒绝、偏好变化拒绝旧准备、真实系统文件保存与元数据导出清理。

- minified-cls-context-build-20261025.txt：R8构建成功。
- minified-cls-context-flow-20261025.txt：checks=39 failures=0。
- minified-cls-context-installed-hash-20261025.txt：实际安装散列。
- minified-cls-context-binary-20261025.json：6个64位ELF满足16KB对齐前提，模型文件合计307948507字节。
- minified-cls-context-scan-20261025.json：38个ZIP成员、395093946字节常见明文密钥格式扫描无发现；不是完整秘密/网络审计。

## 适用范围

本次39项是当前生产源码对应的压缩后二进制回归，补齐旧R8与CLS摘要修复的源码差异。它没有用真实CLS响应驱动此压缩包；CLS解析/摘要实际接入的直接证据仍是cls-hot-context-20261025.md中的单元测试和debug生产采集。

合成评价不证明用户个人收益。模拟器不能证明ARM真机16KB兼容、整体RAM上限、耗电发热、真实账号全文或长期刷新。最终普通发行版本/升级和全页面辅助功能仍未验收。本包只供本地验证。
