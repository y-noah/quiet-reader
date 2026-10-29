# 阅读字号的实际范围与即时预览（2026-10-26）

## 发现与修正

SettingsActivity显示16至28字号，但SeekBar内部0至12也直接暴露给无障碍RangeInfo，导致辅助操作得到不一致范围。预览只在触摸结束draw()时重建，通过无障碍ACTION_SET_PROGRESS没有触摸结束回调，标签/存储更新而预览仍旧字号。

现在SeekBar本身min16/max28/progress为实际字号，初始显示夹在合法范围；不新增或迁移偏好键。onProgressChanged同步更新标签及既有预览TextView，只对真实用户操作保存字号。触摸结束不再重建整个页面，保留原视图/滚动；本轮没有单独测量触摸焦点或滚动保持，不把静态实现描述当成实测结论。setMin自API26可用，与现有最低版本一致，无新增权限或联网。

## 当前证据

扩展无生产类反射的ReleaseAppearanceLayoutProbe，使用实际连接无障碍树的SeekBar RangeInfo/ACTION_SET_PROGRESS；检查实际范围、动作接受、字号存储、预览像素及可见字号文字，并在结束恢复原字体偏好。其他外观选择器、重置取消、返回、按钮48dp和原配置恢复检查保持。

artifacts/local-ai/appearance-font-range-20261026：baseline.txt在原生产代码411dp/1.0字号下43检查/2失败，恰为范围及即时预览。修复后第一轮320dp/1.5深浅各43/1，剩余项是驱动错误用scaledDensity线性换算，不能用于Android新版非线性SP缩放；linear-scale-assertion-dark/light.txt保留。驱动改用TypedValue.applyDimension(COMPLEX_UNIT_SP,28,metrics)，不更改产品字号或阈值；final-dark/light.txt最终各43/0，共86项。实际预览99px与系统期望99px一致，两个最终顶部截图已人工查看；下部内容可滚动，不能将顶部截图当全页内容都在一屏。

最终非debuggable隔离App SHA256 6adfab5fcf32288d103df10a88699d724ac9aacfa074297fc751197868728ea2，与拉取installed-r8-base.apk一致；App/test/映射/清单归档。模拟器恢复420dpi及font_scale1.0，隔离进程匹配0。测试基于连接无障碍操作，不等于完整TalkBack逐项导航/语音验收，也没有重测所有正文/实际账号范围。

## 普通候选

新本地候选New-20261026-ee26445e.apk，SHA256 ee26445ef0300f875c6094cb4d6b6f0ec512960fd4bbc6f0aafd398c32f6857b，319432842字节，取代0f5fbf62；只增加本轮字号产品修复，保留BBC、外观、去重和图片空白修复。原签名app.quietreader/47/0.4.0-upgrade-probe、New、非debuggable、仅INTERNET/ACCESS_NETWORK_STATE；普通BuildConfig测量false已单独保存。v2签名/既有证书、六个64位ELF16KB LOAD对齐、80 ZIP成员常见明文密钥格式扫描通过；不称ARM16KB运行或任意秘密审计。Lint成功，95XML/573单元0失败/错误/跳过。签名及其他包证据和构建日志在同目录。

旧图片51项debug与8项隔离R8覆盖的是旧精确APK；本轮未改图片生产代码，但不能标为这些运行发生在新普通包。当前普通包没有安装手机或模拟器，无提交、推送或发版；真实账号全文、实际本人推荐收益、普通精确升级/运行及ARM全App资源/非充电能耗门槛保持开放。
