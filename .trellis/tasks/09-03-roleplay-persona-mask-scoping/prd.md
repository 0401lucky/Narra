# 角色面具绑定独立化

## Goal

在角色扮演侧边栏切换用户面具时，只影响**当前角色**，不再连带改变其他所有角色的面具。

## Problem / Root Cause

用户反馈：在侧边设置里切换当前角色的用户面具，其他角色的会话也跟着变了。

已定位根因（非玄学，链路确定）：

1. `RoleplaySettingsSidebarPanel.kt` 的快捷卡片标题是「默认面具」，`onClick = onOpenUserMasks`。
2. 三处导航（`RoleplayNavGraph.kt:207`、`RoleplayNavGraph.kt:743`、`ChatNavGraph.kt:138`）都把它导向 `AppRoutes.SETTINGS_USER_MASKS`，即**全局**面具管理页 `UserPersonaMasksScreen`。
3. 该页「设为默认」调用 `SettingsViewModel.setDefaultUserPersonaMask(maskId)`，写入的是 `AppSettings.defaultUserPersonaMaskId` —— 一个全局字段。
4. `AppSettings.resolveUserPersona(maskId = ...)` 在角色未显式绑定面具时回落到 `resolvedDefaultUserPersonaMask()`。因此所有 `scenario.userPersonaMaskId` 为空的角色全部随之改变。

真正的角色级字段 `roleplay_scenarios.userPersonaMaskId` 已存在（迁移见 `ChatDbMigrations.kt:625`），但目前只能在「场景编辑页」`RoleplayScenarioEditScreen.kt` 里修改，侧边栏触达不到。

结论：这是**入口语义错误**，不是数据模型缺陷。修 UI 与写入目标即可，无需改数据库。

## Requirements

1. 角色扮演侧边栏的面具卡片语义改为「本角色面具」：
   - 已绑定时显示所绑面具名称；
   - 未绑定时显示「跟随全局默认：<全局默认面具名>」，没有任何面具时显示「未绑定，使用全局个人资料」。
2. 点击该卡片不再跳转全局页，改为就地弹出 `ModalBottomSheet` 选择面具，选中后写入当前 `scenario.userPersonaMaskId`。
3. 选择列表需包含「跟随全局默认」选项（写入空串），供用户取消角色级绑定。
4. Sheet 底部保留次级入口「管理面具…」，跳转 `AppRoutes.SETTINGS_USER_MASKS` 做面具的增删改。
5. 全局面具管理页的「设为默认」文案改为明确表述：该操作只影响未单独绑定面具的角色。
6. 切换面具后，侧边栏的「当前面具」等身份展示区（`RoleplaySettingsSidebarPanel.kt` 约 1697-1745 行）实时反映新值。

## Constraints

- **不改 Room schema、不升版本号**：`scenario.userPersonaMaskId` 字段已存在。
- 新增 ViewModel 方法须紧贴既有写法：参照 `RoleplayViewModel.updateCurrentScenarioPresetId` 的实现形态与命名。
- 不动 `ChatNavGraph.kt` 中普通聊天页的面具入口语义（普通聊天没有 scenario 概念，跳全局页是正确行为）。
- 用户可见文案全部简体中文。

## Acceptance Criteria

- [ ] 新增 `RoleplayViewModel.updateCurrentScenarioUserPersonaMask(maskId: String)`，仅更新当前 scenario。
- [ ] 单测断言：调用后当前 scenario 的 `userPersonaMaskId` 变更，且 `AppSettings.defaultUserPersonaMaskId` 与其他 scenario 的 `userPersonaMaskId` 均未改变。
- [ ] 单测断言：传入空串可清除角色级绑定，此后该角色回落到全局默认。
- [ ] 手动验证：角色 A 切换面具后，角色 B 的会话面具不变。
- [ ] 侧边栏卡片标题不再是「默认面具」，且点击后弹出选择面具的 Sheet 而非跳转设置页。
- [ ] 全局面具页「设为默认」有明确的作用范围说明文案。
- [ ] `./gradlew.bat app:testDebugUnitTest` 与 `./gradlew.bat app:compileDebugKotlin` 通过。

## Out of Scope

- **会话级（session 级）面具覆盖**：`roleplay_sessions` 不新增 `userPersonaMaskId` 列。同一角色下的多个会话继续共享角色级面具 —— 用户确认当前诉求是「角色之间互不影响」，会话级细分不在本次范围。
- 面具本身的增删改（沿用现有全局面具管理页）。
- 普通聊天（Chat）页的面具绑定模型调整。
