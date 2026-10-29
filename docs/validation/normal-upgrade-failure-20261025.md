# 普通包失败安装与最新构建保留数据

日期：2026-10-25。承接normal-package-upgrade-20261025.md。用于补齐安装失败保护及最新普通构建的数据保持，不是最终正式发行或自动回滚证明。

## 基线与设备

启动已保留的quiet-reader-upgrade-api36/emulator-5562，确认device、sys.boot_completed=1；未重置userdata。原夹具已由真实0.3.35/46升级至较早的本地47验证版，安装散列049bc743f87b34e8e3a6fb0a29825cc4df0d8c95213af138197aec629c2abacc。normal-upgrade-failure-baseline-20261025-072923.txt与pre-failure阶段各11/0，重新核验普通non-debuggable包/版本、两个旧收藏、字号/外观/来源会话模式、旧position/expanded、合成持久Cookie及schema6的幂等导入。

## 失败安装验证

新增scripts/models/test-upgrade-install-failures.ps1，仅允许明确专用模拟器，先验证旧APK已知SHA256和既有状态。不卸载、不pm clear、不使用-d强制降级。

1. 对已装v47执行原版v46的install --no-incremental -r，预期INSTALL_FAILED_VERSION_DOWNGRADE，返回1且匹配。
2. 从新构建复制前1024字节生成独立截断夹具，对其执行同样安装，预期INSTALL_PARSE_FAILED，返回1且匹配。

两次拒绝后设备base.apk散列分别与原基线完全相等；每次再用生产UI/存储验11/0，旧收藏JSON/阅读metadata/合成Cookie不变。原始安装输出与验证阶段为normal-upgrade-install-downgrade-rejected、normal-upgrade-install-malformed-rejected及对应状态txt；机器报告normal-upgrade-install-failures-20261025-073013.json两项passed=true。

这证明两个明确失败情形未替换原包、未破坏已有数据，不等于已验证安装中断/断电、存储耗尽、签名冲突或支持自动降级回滚。测试没有尝试删除签名、绕过系统降级限制或更改用户真实账号。

## 最新普通R8包成功替换

保留旧release-discovery-upgrade App/Test/metadata后，通过build-release-probe.ps1 -Offline -NormalUpgrade构建最新普通包：同applicationId app.quietreader、原签名、专用验证版本47/target36，默认发行版本与品牌不改。

App319341359字节，SHA256 035de8b4ac8e71e5f988786fb77bf1e706eb5af843a4e362fc5cb151cf6788b4；Test1930570字节，SHA256 a1c2fc47203d15df45b357c1bcabc93be73e806c9a2552c5a8a531be06696fea。身份存于release-discovery-upgrade-package.json；设备base.apk散列匹配见normal-upgrade-latest-installed-20261025.json。

此次为既有47验证版→最新47验证版的同版本原签名替换；不冒充再次执行46→47，前者真实升级链仍以原文证据为准。实际替换成功后11/0：normal-upgrade-latest-retained-20261025-073048.txt。随后普通包本地模型/JNI/独立进程释放21/0，normal-upgrade-latest-models-20261025-073107.txt；模型校验/准备3699ms且cold=false，不能称首次冷安装或手机速度。两模型运行后再次11/0，未重复导入旧收藏。

最新普通包6个64位ELF LOAD对齐通过，模型307948507字节；凭证格式扫描38 ZIP成员/395069914解压字节无已识别格式命中，详见normal-upgrade-latest-binary-20261025.json与normal-upgrade-latest-scan-20261025.json。不外推安全审计或ARM功耗。

## 收尾和边界

完成后正常emu kill关闭本轮启动的专用模拟器，保留userdata/最新包与模型；恢复日常5560普通debug/store构建和原签名安装。未提交、推送、发布、修改默认版本/名字/Logo。

实际用户独立兴趣评价、真实平台账号权限与全文、Find X7 Ultra整App峰值RAM/耗电/发热、最终正式版本与精确旧版升级及回滚策略仍未全部完成。合成Cookie不代表真实账号登录，旧阅读metadata保留不代表旧正文锚点视觉恢复；不据此关闭A12或完整交付。
