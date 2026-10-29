# 缓存命中呈现与旧视频标记重验（2026-10-25）

## 生产变更与边界

MainActivity 仅对 Repository 已校验并安装进内存的内存命中、磁盘恢复调用 renderCached，不再重复 cacheArticle。历史恢复仍走原有写入以安装内存；新获取、预读提升、续页与动态正文继续默认写入。没有改 Cookie、namespace、正文版本与年龄检查，没有延长正文有效期。

Repository.reconcileVideo 先检查旧标记存在及采集时间，只有确需清除旧标记时才计算当前 Cookie 指纹并检查 namespace。无标记路径减少一次无用途的 Cookie 调用，实际标记清除仍保留会话校验。

## 当前源码检查

普通 JVM 469/0。当前 debug 隔离包模拟器 article-disk 37/0，实际 Cookie 变更拒绝旧正文、保留阅读位置、命名空间失效、LRU、过期、损坏、磁盘恢复均通过。legacy-video-revalidation 19/0，旧会话/失效命名空间/登录受限正文不能清除旧标记，后续有效文字可清除。

首次安装因模拟器旧测试包签名不匹配失败，随后误启动的旧包 37/0 保存在 cache-hit-article-disk-20261025.txt，不作为本轮源码验证。只卸载模拟器 discoveryprobe 与 discoveryprobe.test 后安装新包，当前结果为 cache-hit-article-disk-current-20261025.txt 与 cache-hit-video-revalidation-current-20261025.txt。未操作真机或原包。

当前 debug 精确包归档：

```json
[
  {
    "file": "app-debug.apk",
    "bytes": 89121710,
    "sha256": "b5de9ece345105f566a7d24f2196bec598695090fa8e51bbef18e749e86f33d0"
  },
  {
    "file": "app-debug-androidTest.apk",
    "bytes": 2021876,
    "sha256": "c140698d14312dba209baaf347bf376effbbf0538157fd63fcdd6d9382779b60"
  }
]
```

## 压缩包回归

新增断言通过解析正文缓存 JSON 的 URL 找到实际文件，在点击前后比较 inode（读取会更新 LRU mtime，所以不把 mtime 当成写入证据）。保存页停止预读、证据时间戳清零并保持500ms，真实打开/展开后检查后台证据恢复及缓存文件仍为原 inode。

当前压缩包及 R8 映射归档 archive-reader-cache-hit-20261025，精确散列：

```json
[
  {
    "file": "release-discovery-flow-app.apk",
    "bytes": 319414958,
    "sha256": "D457C93317D7EFAAB78A4A491AB7446583EC32CBCD0FDFE920953433A0F41129"
  },
  {
    "file": "release-discovery-flow-test.apk",
    "bytes": 2024911,
    "sha256": "DEC7EBF14E4E5A4E2034FEC3FCA820A50B201FABF64BE9D6836FC045C65DC719"
  }
]
```

reader-cache-hit-core-20261025-a 完整37/1。缓存文件inode保持、清零后的后台证据恢复、展开/滚动/返回、冻结快照及偏好持久化通过；失败为少看后要求接纳编号607，实际608。停止隔离App后保留完整SQLite/WAL副本 reader-cache-hit-core-20261025-a-db，607/608均source20/profile22，六个event_id的完整顺序相同（旧显示604为source20/profile20）。因此本次明确为对等有效计算的编号竞态，没有证据表明错误排序被接纳；原37/1结果保留。

驱动将少看接纳也改为观察实际激活身份，要求新personal、来源不旧于已等待结果、偏好版本完全相同，列表真实显示该快照第一条且与被少看的首条不同；仍保持原5秒期限，不改生产排序和自动应用策略。App散列D457C933…不变，驱动DAD728F2DF6F0725F2E2A11749A1012E66FA1D13830D50EB1C65B3BB56BF618D已归档 archive-reader-cache-hit-b-driver-20261025，b轮完整37/0，实际激活614、source20/profile22，新增缓存inode与后台证据恢复再次通过；19轮原始采样，不将周期named采样作为整App峰值证明。功能结果不代替真机速度、实际权限正文、30分钟负载或整App资源验收。


同App/最终驱动的 reader-cache-hit-dual-20261025-a 完整7/0、100事件/6篇、62540ms回放，主机逐条操作身份和时间轴核验通过。首篇Activity/DOM/稳定控件观察4556/6194/6360ms，包含instrumentation等待，不能声明性能改善或等同真机首屏。实际101向量/6精排，点击前已有1条预读正文证据；正文仍为预置合成磁盘内容，不是网络冷下载证明。

core-a 16/17、core-b 19/19、DUAL-a 15/16有效named样本；心跳停顿记录分别7/2/1条，最大3086/2048/1589ms。记录不到不证明无卡顿，周期named PSS也不覆盖所有WebView renderer及采样间峰值。模拟器实际base.apk散列与D457C933…归档一致。全部结果收集后force-stop两个隔离包，ps未见App、ranking或app_process watcher残留。未连接手机、未改变原App、未发版、未提交/推送。

仍待：首篇性能的真机验证、受控30分钟负载与后台、全进程内存/非充电耗电/发热、实际登录权限正文、用户空间提示定位及真实个人效果。当前局部回归不能关闭整体交付门槛。
