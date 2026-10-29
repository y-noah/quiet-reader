# 当前R8精排到真实冷正文（2026-10-26）

结果位于artifacts/local-ai/minified-live-reranker-reading-20261026。personal-reranker.txt为17项通过、0失败；本轮只操作emulator-5560与app.quietreader.discoveryprobe。没有生产逻辑改动。

## 实际边界

ReleaseReadingFlowProbe的live-model-reading新增stage=reranker，默认embedding保留。初始向量/精排输出均空；使用当前资产manifest原始UTF8的SHA、版本examples-rrf-conflict-v8及规范化query/candidate输入独立序列化personal rerank key。只查询这些key且有限logit，不以事件覆盖标记替代个人精排输出，不注入评分。输入为固定公开候选与开发者画像，不能当本人兴趣证据。

目标为BBC繁体ckq6n02lqz8vo/trad，独立捕获33段。目标选择、准备后、实际卡片动作前均无正文缓存，准备后无阅读历史；99条合成缓存正文、1条真实未缓存正文。不删除既有缓存伪造冷读。关闭推测预读/来源采集，通过正常设置“继续整理”启动生产推荐；自主调度另见minified-autonomy-20261026.md。

点击前102向量、3条真正个人精排输出、logit -4.784369、ranking PID10557；公共状态正在本机整理，主线程点击边界再次检查状态。正常ListView卡片performClick打开共同阅读器；无生产反射、keep规则旁路或人工ITEM回调。

## 结果与范围

模型进程退出观察243ms；实际网络正文DOM观察5217ms，33/33段含尾段，普通原生缓存有新时间、精确URL及BBC节身份。resumed226ms、postResumed227ms、firstDecorDraw433ms均是测试生命周期/装饰层观察，不是正文呈现帧。不同文章与网络不可与上一轮比较后声称性能提升。

加载阶段不增加内容或平台偏好，首页冻结快照不变。正文DOM后自然截图采样351ms取得非空画面，已人工查看R8-reranker-reading.png：标题、署名、正文可见，图片仍占位。截图探针不调用强制draw/invalidate；351ms含测试开销，不作为呈现帧基准。只证明本样本正文画面，未证明整篇图片完成、全平台/真实账号或触摸流畅度。

App SHA efd63a237963e00cf2461b1531b888265fdfedb1bb513fde04c537b3c3be133e与实际安装base.apk一致；驱动5c0f81a2b0c49ec3429106eae8c4cda1f9c30de45e9202bdcd6ac5f29bd9e05b。package.json、mapping.txt、installed-app-sha256.txt及严格summary.json归档。两个隔离包均已停止，ps匹配0，运行会话64692以0结束。

本轮关闭前轮“未验证精排阶段打开真实冷正文”的局部缺口，不关闭真实账号权限、本人推荐收益、ARM全进程峰值/长期耗电和正式普通包精确升级运行等门槛。普通候选6941fe60…未因驱动变化重建或安装；没有手机操作、提交、推送或发版。
