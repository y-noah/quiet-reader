# 评价结果回看：精确R8包与稳定画面

日期：2026-10-25。补齐recommendation-trial-history-20261025.md中最新回看改动尚无精确R8证据的缺口；不关闭个人效果或全App真机门槛。

## 受测身份

保留旧Flow App/Test/metadata，构建build-release-probe.ps1 -Offline -FlowInstall成功；隔离包app.quietreader.flowprobe以原签名覆盖安装API36/emulator-5560，未卸载/清数据。App319341343字节，SHA256 13f947aeac8f421f47c5818510b4c598f018471ae1f9f73f4de787752c0c70f2。设备base.apk散列匹配，见minified-trial-history-installed-20261025.json。

首轮Test SHA256 445a5733d98e45e2fe2ff8b1169784138309b75cee4fbd348568358a07d6c4e0；仅增加弹窗截图前500ms等待后的最终Test SHA256 bea2d8840b1d17ba0072456927d2252221cca818e5e5d290e0b3aee65584041f，均1930570字节。最终release-discovery-flow-package.json记录后者。第二轮明确检查生产App散列未变，只替换签名测试驱动。

## 结果

ReleaseReadingFlowProbe增加实际完成评价后经偏好菜单打开最近完成结果，确认6个合成事件想看/不想看/不确定各2，双模型前排2/6及活动快照/反馈未变；未新增生产keep规则或反射生产字段。推荐→确认更新→收藏→统一阅读→反馈→配对评价的原检查保留。

首轮26/0，日志minified-trial-history-20261025-073900.txt；稳定画面复验26/0，日志minified-trial-history-stable-*.txt。所有标签均为合成选择，不是个人推荐效益；六个候选全入三顺序前20，计数相同是夹具设计，不表示三种排序效果相同。

首截图抓到原生弹窗入场动画过渡帧，出现背景文字透出，不作为视觉通过。仅测试在截图前等待500ms，复验生产APK散列保持一致。稳定图已取回quiet-reader-review/minified-trial-history-stable-20261025.png并查看：白色弹窗背景完整覆盖后方卡片，内容无本页可见重叠，关闭可见。不是生产透明度修复，不以该画面证明全部字号/暗色或TalkBack。

六个64位原生库LOAD对齐通过，模型307948507字节；已识别明文凭据格式扫描38 ZIP成员/395070982解压字节无命中。见minified-trial-history-binary-20261025.json与minified-trial-history-scan-20261025.json。此为二进制前置检查，不是ARM运行或完整安全审计。

结束已恢复普通debug/store构建并以原签名安装至日常模拟器。JVM有效449/0，本次复用未变结果，不声称全部重新执行。未提交、推送、发布或修改名称/Logo。

## 整体边界

交付缺口按delivery-gap-audit-20261025.md追踪：个人事件/时间独立评价采集与收益，实际账号阅读全文，Find X7 Ultra整App内存/功耗，完整辅助功能与最终普通发行链仍未完成。此报告只补齐当前回看UI的压缩兼容及一张稳定画面。
