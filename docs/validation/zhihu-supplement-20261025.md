# 知乎问题补充身份及部分内容提示

日期：2026-10-25。修复可复现的解析/合并问题；不声称已取得真实登录账号的完整问题与回答。

## 问题

QuestionRichText在没有来源DOM id时按正文hash生成Section身份，摘要和展开后正文因此具有不同身份。AnswerStream.merge可能将较长正文当成另一条问题补充追加，而非替换原摘要。另一个确定问题是SourceParser先设置部分内容提示，随后被知乎通用阅读说明无条件覆盖。

## 修改

- 对合法知乎question/ID（含同问题answer永久链接）的QuestionRichText，使用问题ID生成稳定身份。文章页或其他不匹配URL不套用问题身份。
- 同一页面有桌面/移动两个补充容器时，保留一份较长正文并重建一致的扁平blocks。
- 同一问题续读合并完整补充时，识别旧“问题补充”缓存并保留第一份已读Section.id，以保留展开状态与段落锚点；选择较长正文，清理同问题旧重复补充。只有同问题、明确非回答且新身份符合该问题时才走兼容路径，不把回答或其他问题合并成补充。
- 知乎阅读说明追加到已有提示之后，不再覆盖来源可能只返回部分内容的提示。未增加绕过来源权限或猜测缺失正文的路径。

## 单元与Android验证

新增3个回归用例时基线441项/3失败，分别复现身份变化、旧缓存重复及部分提示覆盖；摘要保存在artifacts/local-ai/zhihu-supplement-baseline-20261025.txt。随后增加同页双容器用例，最终JVM442/0，覆盖同问题永久链接一致、旧段落ID保留、跨问题不合并、完整尾文与未知部分说明。

DiscussionFlowProbe的知乎路径改为实际SourceParser解析更长补充及新回答，经AnswerStream合并后调用正式阅读器追加路径；检查原问题段落CSS位置、补充容器仅一份、旧段落anchor仍存在、展开后的新尾部已进入DOM，以及原metadata仍保留。

首轮61/1：新增断言误把Section内部id当成DOM section id。ReaderHtml的section id本来就是按索引生成，稳定身份用于data-reading-anchor段落；原位置与取得metadata检查均通过。测试改为检查明确补充容器和ReaderHtml.anchorId生成的原段落身份，不修改生产布局或放宽正文尾部要求。保留失败日志zhihu-supplement-flow-20261025-071026.txt。

最终实际讨论链61/0，日志zhihu-supplement-final-20261025-071207.txt；会话变化/HTTP权限限制受控回归33/0，日志zhihu-supplement-cookie-20261025-071240.txt。后者经过真实WebView/DynamicReader及解析器，仍是合成host-only Cookie和受控HTTP错误，不是用户登录验证。

## 边界

Android来源正文为受控合成结构，不是知乎线上完整读取或真实登录会话证据。真实补充展开、后续回答与账号切换仍需合法来源样本；来源403/401仍应明确显示限制。个人独立留出、Find X7 Ultra资源及最终发行包等原门槛保持开放。未提交、推送、发版或修改品牌。
