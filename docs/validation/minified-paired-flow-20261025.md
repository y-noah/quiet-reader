# 最新压缩包：推荐、阅读与盲评联通

日期：2026-10-25。承接minified-reading-flow-20261025.md及paired-trial-20261025.md；本轮证明最新生产改动可以经过R8并在非debuggable包运行，不替代最终普通发行包或真机验收。

## 构建与身份

先保留旧release-discovery-flow的App/Test/metadata副本，再执行build-release-probe.ps1 -Offline -FlowInstall。专用包名app.quietreader.flowprobe，品牌及普通版本未变。两个APK均原签名覆盖安装至API36/emulator-5560，未卸载或清除普通包/升级夹具。

最新App：319341343字节，SHA256 fdfa523cfb791da1103714ed8956890313fbeb5c3e9c1787ec0441a67c1816e9。minified-paired-flow-installed-20261025.json记录设备base.apk散列与本地受测包一致。配套Test散列保存在release-discovery-flow-package.json。

二进制check_apk检查arm64-v8a和x86_64共6个原生库的ELF LOAD对齐，failures为空；模型资产307948507字节。明文凭证格式扫描遍历38个ZIP成员、395067542解压字节，findings为空，仅证明已识别格式未匹配。产物分别为minified-paired-flow-binary-20261025.json和minified-paired-flow-scan-20261025.json，不据此证明ARM运行、完整安全审计或整机功耗。

## 实际流程：25项、0失败

ReleaseReadingFlowProbe沿用明确合成的六条中文候选/正文、开发者手机电池兴趣、真实模型准备和模型计算；专用包内的合成推荐表可重置，不引用生产类或R8字段，不增加keep规则。

原推荐→冻结卡片→确认更新→收藏→稍后读→实际阅读/展开/滚动/返回→少看反馈→新结果确认链仍通过。本轮新增可选对比入口，在真实非debuggable的生产数据库保存paired记录；六个不同事件逐条经原生按钮评价，三种选择各两次。评价时不显示排序模式，结束显示各顺序想看2/6、未知2；检查三顺序/配对类型/非独立留出标记均保留。评价前后活动快照与兴趣反馈数量相同。

完整日志为artifacts/local-ai/minified-paired-flow-*时间戳.txt。此次六候选全部落在每种顺序前20内，因此三个计数相同是夹具必然结果，不能解释为排序效果相同；真实100候选差异见独立接口回放证据，个人收益仍没有标签证明。

最新实际展开截图已取回至安全审阅目录quiet-reader-review/minified-latest-reader-20261025.png并查看。当前浅色正文有正常换行、段间距、顶部返回与收起控件，没有本页可见文字重叠或横向裁切；不代表所有页面、长图/评论或完整视觉验收。

## 恢复与未完成

随后重新构建store普通debug测试环境并以原签名覆盖安装成功；现有JVM有效结果436/0，构建复用未变测试结果，不声称本轮全部重新执行。未改生产功能，仅扩展压缩包流程探针和记录证据。未提交、推送、发布或修改品牌。

最终普通包版本/精确发行前驱升级与失败/回滚、独立事件/时间个人评价、真实账号阅读，以及Find X7 Ultra整App内存/耗电/发热等既有门槛仍开放。此诊断包不作为完成发布交付。
