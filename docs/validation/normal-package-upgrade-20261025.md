# 保留原版0.3.35到普通包版本递增升级（2026-10-25）

## 与已有隔离升级证据的区别

本轮使用本地保留的News-0.3.35.apk实际安装，普通applicationId为app.quietreader、versionCode46、targetSdk35；以同证书、同applicationId、versionCode47、targetSdk36的当前non-debuggable/R8包进行adb install -r替换。不是修改包名后的夹具，也不是两个versionCode46包的替换。

原APK的来源仅声明为本地保留文件，本轮没有与GitHub公开发行资产下载哈希核对。没有提交、推送或发布。新增normalUpgradeProbe构建属性只为本地诊断把版本设为0.4.0-upgrade-probe/47，默认app/build.gradle仍为0.3.35/46，未决定最终发行版本、名称或Logo。build-release-probe.ps1的-NormalUpgrade与两个隔离包开关互斥，产物使用独立release-discovery-upgrade前缀。

## 独立设备与安全边界

创建quiet-reader-upgrade-api36，emulator-5562，从已有Android36系统镜像离线创建，不下载镜像、不复制或清除日常AVD数据。setup-emulator新增-UpgradeProbe/-Offline，start-emulator新增该明确名称与5562端口；其他默认参数保持原状。

avdmanager报告devices.xml加载警告但退出成功。后续独立确认AVD配置、运行PID13988、ADB状态device、sys.boot_completed=1，真正安装/执行测试后才计为准备完成。初次设备offline时进程仍在运行，未重复启动。该AVD的2GB RAM只是PC模拟器配置，不是手机App资源结论。

ReleaseNormalUpgradeProbe仅接受ranchu/sdk_gphone、API36、普通包及明确旧/新版本/target SDK、non-debuggable。seed拒绝已有marker、推荐数据库、收藏或阅读历史。所有数据均合成；用Android标准存储、CookieManager、SQLite和生产UI，不引用生产类、反射混淆字段或加keep规则。原包首次安装到空AVD，新包只-r替换；没有卸载/pm clear/降级或执行回滚。

## 精确安装链

normal-package-upgrade-chain-20261025.json记录aapt元数据、apksigner验签和SHA256。

| 文件 | 版本与target | 字节 | SHA256 |
|---|---|---:|---|
| News-0.3.35.apk | 0.3.35 / 46，target35 | 185742 | 6310e4579497f59134aaadbf81c128ed0a0f341445b200f881dd5583e94e9aa2 |
| release-discovery-upgrade-app.apk | 0.4.0-upgrade-probe / 47，target36 | 319324975 | 049bc743f87b34e8e3a6fb0a29825cc4df0d8c95213af138197aec629c2abacc |
| 最终测试APK | app.quietreader.test | 2561260 | f64d5be4ac0b0649e169314eebbc7c97e6097badc94acaa27c7f2bb69ae8b103 |

两个App验签通过，证书SHA256均为ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37。旧包由上述明确文件安装；新包实际设备base.apk的SHA256与留档一致，见normal-package-upgrade-installed-match-20261025.json。构建日志build-normal-package-upgrade-20261025.txt及后续测试驱动构建日志均保留，App二进制未因测试修正而改变。

## 原格式现场核验与失败保留

最初seed给收藏Item多加了snippet。真正旧v46二进制启动后重写为其支持的source/title/url/detail/video，去掉snippet，故字节相等检查失败：normal-package-upgrade-seed-20261025-api36.txt为9项、1失败；normal-package-original-inspect-20261025-api36.txt为7项、1失败，输出只包含本探针合成收藏。

这不是当前升级生产代码删除摘要，而是测试误用了新Item格式。新增original-verify阶段，不重新seed或改写任何App偏好：严格确认唯一差异是旧二进制不支持的snippet，全部旧字段相同，然后把实际旧格式存入独立诊断marker作为升级基线。之后新/旧App偏好必须与此真实基线逐字节相等。未来新seed直接写真实旧结构。

normal-package-original-format-20261025-api36.txt：8/0，原始普通release仍为v46/target35，旧结构、字号/外观、来源会话模式、阅读metadata及合成持久Cookie均确认。没有将最初错误夹具结果改名为通过。

## 实际升级与结果

原版两个收藏为V2EX讨论和知乎问题。legacy MainActivity.saved保持原字符串，阅读position321与expanded列表、font23、mode2/spacing2、ZHIHU-desktop/V2EX-web及.invalid域持久Cookie均保留；Cookie值不导出。

- normal-package-upgrade-verify-20261025-api36.txt：11/0。真实版本递增安装后，生产发现→稍后读显示两条收藏，schema6数据库正确生成，saved中恰好两个不同ID，legacy_saved_imported已提交，原偏好/metadata/Cookie保持。
- normal-package-upgrade-cold-models-20261025-api36.txt：22/0。升级后的第一次模型准备，确认local-models不存在；实际模型校验/复制3803ms、embedding归一化512维、reranker有限logit与SentencePiece JNI、独立进程及释放退出通过。报告旧标签使用cold install，但此处严格指升级后的首次模型准备，不是全新App安装；模拟器3803ms不外推手机速度。
- normal-package-upgrade-repeat-20261025-api36.txt：11/0。模型初始化后再次启动，仍仅两个收藏，没有重复导入，旧状态继续保持。
- normal-package-upgrade-alignment-20261025.json：六个64位库所有LOAD段16KB对齐，0失败；不是ARM实机验证。
- normal-package-upgrade-credentials-20261025.json：全部ZIP成员已知明文密钥格式0命中；不证明任意秘密缺失。

## 收尾与未关闭门槛

新AVD已通过adb emu kill正常停止，PID13988已消失，userdata/升级后App/模型及夹具保留，未删除共享.tools或任何已有AVD。build-store-after-normal-upgrade-20261025.txt和install-store-after-normal-upgrade-20261025.txt记录恢复日常emulator-5560的普通debug/store配置及原签名-r安装；没有在v47专用AVD上强制降回v46。当前JVM XML425项，失败/错误/跳过均0；构建可复用结果，不称全部重新执行。diff与新增探针空白/冲突标记检查通过。

本轮补上真实普通包、原签名、版本递增和旧收藏导入的一条升级路径，不能替代实际发布包最终版本/回滚选择。合成Cookie非知乎/微博真实账号，阅读metadata保持非旧正文相同段落恢复；未执行真实登录全文、整站权限/评论、故障中断、用户个人独立留出、Find X7 Ultra RAM/能耗/发热及全部TalkBack等验收。总体目标保持未完成，品牌保持原样。
