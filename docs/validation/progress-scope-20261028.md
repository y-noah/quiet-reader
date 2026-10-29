# 推荐刷新作用域开销与后台预筛

状态：本地生产优化，尚不代表整体推荐质量、ARM帧率或耗电验收。不操作手机，不推送/发版；手机与current-candidate仍c315361c。

## 实际问题与测量范围

原ArticleProgressBinding.Scope同时保存一套namespace/session和ArticleSession内部另一套身份。构造加首次复核会对同一URL调用4次CookieManager，后续current调用2次。显式刷新在UI线程为前128个支持新闻进展的候选全量捕获，再在发布时全部复核，即使数据库根本没有正文更新证据也如此。

目录artifacts/local-ai/progress-scope-20261028/：

- before.txt：API36模拟器，真实CookieManager，随机隔离.invalid域合成1800字节Cookie，128条捕获/复核，2轮预热后记录5轮。捕获wall范围285.23～872.82ms，中位324.18ms；复核109.53～203.45ms，中位141.47ms。包含慢轮，不删样本。
- controller-before.txt：实际首页70条中文新闻合成候选、真实圆点点击，存储队列受控阻塞以隔离同步UI处理；同步wall365.60ms、主线程CPU200.58ms。其余原7项竞态检查通过，性能问题并不会使功能测试失败。

这些是有限模拟器诊断，非真实用户候选、全程刷新延迟、手机帧率或功耗结果。

## 改动

1. ArticleSession提供自身捕获的不可变scope身份和文档归属检查。Progress Scope只使用这一套令牌，保持既有持久key格式与Cookie指纹规则；捕获/初验2次Cookie读取，current1次，账号变化仍失效。
2. 显式刷新先捕获只含缓存namespace/epoch的动作上下文，再在storage线程查询最多128个候选的标量进展元数据。只有已存有未消耗证明的文章才回UI捕获并最终复核账号scope。空证据常见路径不再全量读取Cookie。
3. 后台预筛不授权重推：原精确账号scope、整事件最近展示/阅读时间、来源/偏好/筛选/成员元数据校验仍全部执行。预筛期间新增的证据可等下一次明确刷新，不自动替换当前阅读。
4. 预筛/排名/发布均检查动作代次和缓存上下文；连续点击合并、离开取消、新pending追读上限不变。后台错误退回普通冷却，未添加定时器、服务、模型推理或后台循环。
5. 没有来源更新时间证据的Document在捕获进展scope之前直接退出可选模块，避免无意义Cookie读取；原阅读反馈与正文准入不变。

## Debug证据

- recommendation-refresh-after.txt与final.txt：各8/0，实际圆点同步wall分别5.63/7.22ms，CPU3.56/6.11ms。新增真实Controller→后台预筛→实际scope→持久进展→同质量层提升链路；合成正文hash证明不作为真实语义重要性证据。原账号namespace变化及后台结果不激活检查保留。
- article-progress-scope-after/final.txt各5/0：真实Cookie变更拒绝旧token、scope和Document，新cookie生成不同持久key；namespace变化失效。128全量捕获的优化后wall仍112～155ms左右，说明极端大量真实进展时仍不能宣称主线程零开销。本改动主要避免普通刷新无证据时走此全量工作。
- article-progress-history-after/final.txt各35/0：预筛只返回请求集合中仍有有效证明的文章，证明被消费后不再触发账号捕获；原迁移/回滚/容量/实际阅读基线仍通过。
- pending-acceptance-race-after.txt15/0；refresh-snapshot-final.txt14/0、explicit-feed-cache-action-final.txt6/0、update-dot-top-final.txt13/0。
- build-final.txt完整764 JVM/112套，0失败/错误/跳过；lint.txt Debug/Release成功。

## R8与边界

r8-recommendation-refresh-news.txt和r8-recommendation-refresh.txt各7/0，分别使用澎湃格式受控候选验证后台预筛常见路径，以及V2EX格式验证不支持新闻进展时的直接路径。都通过实际自然曝光、屏外不曝光、真实圆点换批、质量层保存与无偏好反馈检查。R8两项均无正文进展证明，正向证明重推来自上述Debug实际Controller链路，不扩大表述为混淆进展重推专项。

不扩大新闻进展的适用范围：仍限同文章明确新增正文；跨URL后续、讨论进展、用户真实收益、来源供给及手机资源门槛仍开放。名称沿用用户后来明确选定的New，本轮没有品牌改动。

## 普通候选与收尾

原签名保留userdata升级：upgrade-before/after/repeat各12/0，normal-models19/0，共55项。保留既有schema9及旧设置/阅读位置/收藏/模拟持久Cookie，真实双模型与JNI、释放后退出通过；不是平台真实登录权限证明。

本地普通候选归档normal/app.apk，SHA256 60fd94c5662d0243bf59e9694e330928d591f2fd8b5aaea0a516472aec0f5e51，319466194字节。installed-hash.json核对实际安装base.apk完全一致。R8和驱动散列见package-hashes.json，最终成功终态见terminal-summary.json。未更新手机或current-candidate，未推送/发版；测试模拟器关闭并保留userdata。
