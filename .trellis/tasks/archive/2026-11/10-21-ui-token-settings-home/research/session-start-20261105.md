# P10 设置首页：执行起点（2026-11-05）

- 用户在进度盘点后要求继续下一批，并明确回复“接纳侧栏，完成收尾后做设置首页”；随后确认P05的具体提交清单、归档及继续。
- P05已完成：实现`4d32dabf`、基准`0c272255`、归档`78d2a230`、日志`18c1613f`。记录位于[已归档验证](../../10-21-ui-token-session-sidebar/verification.md)，不再等待P05视觉确认。
- 本批基点为`main / 18c1613f`；启动前`app/src/main`和`app/src/test/java`无未提交改动。既有历史任务搬移、用户资料及manifest老CI段落保持，起点状态见[清单](workspace-start-status.txt)。
- 已冻结全部110张已接纳PNG：[SHA-256清单](accepted-png-hashes-before-p10.txt)，与P05最终接纳清单一致。
- 现有P10已执行`task.py start`，为当前`in_progress`任务；implement/check各12条上下文校验通过，不新建重复任务，也不重走已批准的方向确认。

## 本轮所有权与次序

- 实现代理独占`SettingsScreen.kt`、`SettingsComponents.kt`及本批测试/截图登记/源码守卫；必要通用窗口捕获改动仅限测试层，并保持P05行为。
- 主会话负责任务记录、版本/spec、对照页、人工接纳与具体提交；独立检查由后续`trellis-check`完成。
- 先用真实SettingsScreen取得16张before：三主题浅深6、固定动态浅深2、200%窄屏首页2、三类Sheet浅深6。然后迁移首页及显式opt-in接口，再冻结同参数after。
- 110张旧图保持；新16张未经用户确认不进入snapshots，不record或豁免计数守卫。新增候选阶段127项截图中的缺图失败按实际报告单列。
- 保留设置首页实际入口、草稿/保存/消息消费及返回立即pop行为；P11、版本/更新源、设备与远端发布均不随本批推进。

尚未取得P10实现与测试结果；P05的1719/111结果只用于前置核实，不冒称本批已通过。
