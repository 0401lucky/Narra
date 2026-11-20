# D2 独立检查进度

版本：2.4.1。检查者独占生产、测试与 Gradle，当前尚未释放；原生设备由主会话最终执行。未提交、推送、发布或调用真实模型/TTS。

## 已核证的基线

- 逐项核验作者 `implementation-final-source-hashes.json`：1392 项均吻合；相对 D2 开始基线的 44 项修改与 `implementation-files.json` 完全一致。
- 读取两个作者原始 XML 目录：361 项/24 类及 80 项/6 类全过；按 class+method 去重为 385 项/27 类，未将交叠计作新增覆盖。
- 已读取完整 hook 保存文件、check.jsonl 各规范/研究及父、D2 的 PRD/design/implement/矩阵；审查沿真实导航、VM、Room、上下文与最终 HTTP 路径。

## 本轮自修

1. **恢复动作错误**：首次 artifact 落盘失败后刷新丢掉免费 SAVE；已持久截断产物刷新却显示 SAVE。现在通过 D1 实际有界 received 缓存及 `canSaveGeneratedArtifact` 判断内存产物，完整性单独决定动作；未知请求不伪装为已收结果。
2. **claim 存储失败无入口**：INPUT 已存但 REQUESTED 写入失败后只留下通用错误，`recoveryAction=NONE`。现在保留显式 GENERATE 动作；零网络且不重复输入。
3. **旧场补存不可达**：原 UI 只恢复当前非 ENDED 场次最后一轮。新增真实路由中的“待保存结果”，以当前角色/persona/run 的有效上下文查询；Room 使用原提交守卫、hash 与完成证据筛选。线上返回后和新 B 场均可补存 A，保持模式/运行/当前场不变。DRAFT/marker/未完整/跨身份/失效来源不进入列表。
4. **失效完整产物阻塞显式重试**：RESULT_READY 完整标志原先无条件阻止新 attempt，来源更正后无法补存也无法重试。现在仅在明确 allowUncertainRetry 且旧提交守卫已失效时准许重冻来源；合法完整结果仍优先免费补存。
5. **同场景旧入口残留**：路由 wrapper 原只按 scenarioId 判断归属。现显式路由还比较 entryId，防止 A→B 首帧显示同会话旧候选/动作；旧无 entryId 恢复继续支持。
6. **图片草稿提前清空**：原 Screen 以 isSending 清待发图片，而陪伴 busy 在输入落库前已变 true。新可选 `onSendWithInputSaved` 回调只在 INPUT 保存成功后清除对应图片快照；原线上 NONE 的补存/刷新 busy 也不再混作发送。

## 当前证据

- `check-red-recovery-01-results/`：3 个新增回归先红，断言分别为期望 GENERATE 实际 NONE、期望 SAVE 实际 GENERATE、期望 GENERATE 实际 SAVE。
- `check-red-stale-artifact-01.xml`：来源更正后的显式重试先红，attempt 未变且未发生第二次 HTTP。
- `check-final-jvm-results/` 与 `check-final-jvm-summary.json`：修复后 **109 项/11 类、0 failures、0 skipped**。
- `check-final-jvm-source-hashes.json`：该次通过对应源码；本轮 16 个稳定修改文件已由 Android Studio formatter 完成。
- 原生入口/恢复测试目前 17 项，图片回执回归由独立 UI 执行者在唯一新测试文件补充；尚未作为运行通过。
- 最终 Lint、baseline/AndroidTest 编译、双 APK、受影响旧截图 verify 和最后哈希汇总仍待完成，以 check-final 为准。

## 规范交接要点

- `CompanionRepository.canSaveGeneratedArtifact(id, owner)` 与实际首次产物保存共用守卫，不能靠 REQUESTED/UNCERTAIN 推定已经收到正文。
- `CompanionTurnCoordinator.pendingArtifact` 只返回本进程真实、仍可存储的缓存；结束/淘汰/来源变化后清理无效缓存。首次全落盘失败的结果不能承诺离场或进程死亡后仍可找回。
- `listPendingResults(context)`、`CompanionRequestCoordinator.pendingResults(scenarioId)` / `savePendingResult(scenarioId, attemptId)` 是受控补存入口，不激活旧场、不改变记忆用途、不发新请求。
- 新原生 UI 必须独立记录方法数/原始结果；模型角色表达、物理设备体验仍不能由 Fake 或 HTTP fixture 推定通过。
