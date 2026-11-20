# D1 验收与 D2 交接

2026-11-10：D1 工程验收通过，允许 D2 接入。父任务仍需完成 D2–D6；本轮未提交或发布。

## 已验证范围

- 场次/turn/attempt、持久 claim、原文与生成产物分阶段保存、安全替换和失效。
- waiting INPUT、CONTROL_NOOP、统一来源资格与 v2.4 自动/手动/兼容/摘要链。
- 三协议完成证据及旧消费者兼容，54→55 迁移、固定旧库与 S3 配套。
- 独立检查修复五项恢复/并发/来源问题和一项设备测试夹具兼容问题。

## 实际证据

- [独立检查](check-final.md) 与 [验证 JSON](check-final-validation.json)：联合 1182 项、最后定向 86 项，合并去重 **1184 项、133 类**通过；编译、Lint 和双 APK 通过。
- [设备验收](device-final-validation.json)：Android 36 专用 `Narra_Memory_V24`，**33 项、10 类**通过。首轮 29 过/4 失败；修复 fixture 后四项定向复验通过，按唯一用例采用最新结果，不宣称单次 33 项全过。
- 首轮失败发生在固定 54 fixture 读取省略的 `notNull` 标志；只改测试 helper，主 APK 未变化，全部迁移断言保留。原始失败和复验日志均保留。
- 最终 1367 项源码与冻结记录一致，273 项旧 schema/截图/参考保护项逐字节未变。AVD 与本轮构建进程已正常关闭。
- 主 APK SHA-256：`3d818deb173a8499ccb99894ba3e8e68a4560d04d31b917e3def64f364ed4c2f`；测试 APK：`6dd5fe0b0ec7670523fd37eb5770791ebd97cb7542eea0c0bc212ee071527167`。

## 接线合同

完整可执行规则见 [陪伴场次规范](../../../../../spec/backend/companion-foundation-guidelines.md)。

1. 直接开始只创建 DIRECT/ACTIVE；预览调用 OPENING，只有 `acceptOpening` 成功才进入正式场次。普通恢复不创建新场，不自动请求。
2. `generate`、`retrySave`、`acceptOpening` 分开。NeedsSave(false) 仅保证当前进程结果；已持久产物可免费补存。开场接受失败重试接受，不把 Generated 当 Saved。
3. 输入编辑后，未提交 turn 的 activeAttemptId 可能指向 CANCELLED/UNCERTAIN 的恢复记录；结合 state 判断，不视为仍在网络请求。显式新 attempt 才重冻最新输入/来源。
4. 新回合、来源编辑/删除、运行/身份变化会阻止旧结果覆盖；旧 COMMITTED 回执对应版本已改变时拒绝，不返回后来新答。
5. 结束旧场或回线上后可仅补有效旧产物，不重新激活；废弃 DRAFT 不能借恢复发布。UI 仍须按 encounter/generation 隔离迟到结果。
6. 模型授权历史由 D2 构建，不能从 UI 窗口兜底。新消息统一使用实际关联图的来源资格；typed CONTINUE 不按字符串识别。

D2 负责真实入口、跳过新路径的静态 greeting、origin 返回、授权聚合、请求局部召回与时间策略。D1 未验证真实模型/TTS效果、物理设备体验或 D2 的产品入口。
