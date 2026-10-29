# 来源设置与阅读样式：当前混淆包验证（2026-10-25）

## 变更与精确包

最新来源入口移至设置→内容来源，底栏发现/稍后读；阅读器长作者独立标签、简短展开/收起、48CSSpx操作目标及aria状态一并进入R8验证。默认品牌、logo及版本仍News、46/0.3.35；本包applicationId为隔离验证用途app.quietreader.flowprobe，不是普通发行或升级包，未发布。

App SHA256 CCDECDAB78D9F93F35C6E63FB5C3D64EB91202C01371A392F77E9FA8BAF49C0F，319357727字节。已安装模拟器base.apk的SHA一致，见settings-reader-minified-installed-hash-20261025.json。最终手机验证驱动SHA256 04E8ACF9D581C472A626045161D318B715685B64E634AB0127E389F3429298B6，1979722字节。settings-reader-final-service-manifest-20261025.json确认App和最终驱动均不声明临时辅助功能服务。

辅助功能空服务的Manifest和XML仅由discoveryStoreUi配置引用，位于src/discoveryStoreTest；普通App、releaseDiscoveryUi手机测试驱动不引用。服务类留在测试源码不构成已安装服务声明。此前驱动B99FBD2D5D7A17AC0CA0C5AC467164D26A03DD5A8CEC9CC6A4E55D7BE9000836带被动测试声明，仅用于模拟器；已归档archive-r8-before-service-scope-20261025，未给手机安装。

## 实际结果

- settings-reader-minified-build-20261025.txt及settings-reader-final-minified-build-20261025.txt：两次离线R8构建成功。收紧测试组件范围后App hash未变化，只改变测试驱动。
- settings-reader-minified-core-20261025.txt：App CCDE…搭配早期B99…驱动，35/0。
- settings-reader-minified-navigation-models-20261025.txt：同App搭配B99…驱动，20/0；包含发现/稍后读/设置实际入口、设置→内容来源→返回、修复模型UI、独立进程双模型真实输出、JNI与释放后退出。不是冷安装首启计时。
- settings-reader-final-minified-core-20261025.txt：最终04E8…驱动配合同一App，再次35/0。真实本地模型、候选排序待接纳、收藏、统一正文展开、正文超出摘要、滚动/返回、弱反馈边界、平台反馈、盲评价/未来池协议均在范围内。
- 已查看当前混淆包展开截图，作者与简短收起入口分开，段落正常换行；这是合成缓存正文截图，不是真实平台质量或完整视觉验收。
- 当前JVM466/0；模型及阅读数据为受控合成/隔离数据。core模式明确排除SAF，不创建公共导出文件。

上一轮实际手机App 0f00…、驱动与R8 mapping已归档artifacts/local-ai/archive-before-settings-reader-20261025，并修正findx7-first-device-20261025.md的历史元信息指向。不能把此前手机35/0、22/0当作CCDE…当前App的真机结果。

## 未关闭项

当前证据为API36项目模拟器的隔离R8 App；尚不是最终普通签名版本/保数据升级全路径、实际登录全文、个人5会话兴趣收益、手机全进程1GB峰值或长会话耗电/发热证明。完整TalkBack人工听读/全页面顺序与空间紧张提示仍待验证。未再连接手机、未提交、推送、发版、改名或更换logo。

模拟器专属配置补验：reader-store-scoped-service-build-20261025.txt构建成功，reader-store-scoped-service-control-20261025.txt默认控制3/0，服务设置恢复两项True。它属于store测试包，独立于上述release手机驱动。
