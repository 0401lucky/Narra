# 实现代理验证记录

- 2026-10-21：`./gradlew.bat :app:compileDebugKotlin --console=plain`，exit 0，42 秒，9 tasks（2 executed / 7 up-to-date）。日志：`compile-implementation-1.log`。此为中间编译，后续仍有 UI 与测试修改，不能作为最终门禁。
- 基准 PNG / manifest 未由实现代理修改；没有执行 record 或 verifyAndRecord。
- 中间批次 `testDebugUnitTest --tests RoleplaySemanticColorsTest --tests UiTokenSourceGuardTest`：exit 1（27 秒），14 tests / 1 failure。新颜色4/4通过，旧守卫10中1失败；失败为迁移范围内旧线下声明/分割线对齐与顶部副标题的精确例外登记缺失，已交守卫owner修复。日志 `tests-colors-guard-1.log`。后续守卫新增用例，计数另行记录。
- 设备验证由主会话探测：当前无连接设备。后续宿主行为测试与设备测试源码编译分别登记。
- 第二批 `tests-pilot-2.log`：生产编译通过，但测试编译 exit 1，`PilotAccessibilityTest` 误导入成员方法 `onNode`，没有执行测试；已删除无效 import。
- 第三批 `tests-pilot-3.log`：exit 1（27 秒），28 tests / 4 failures。Highlight 3/3、SemanticColors 4/4 全过；ThemeSwitch 3/4（唯一失败是气泡 tag 在unmerged tree，已修）；Accessibility 3/5（P03同tag查找已修，P01底栏文本overflow继续定位）；Guard 11/12（新增实际合成端点的精确颜色例外计数待owner更新）。白/灰/黑图片真实合成、缓存generation/identity、固定动态P03已取得通过结果。
- 第四批 `tests-pilot-4.log`：exit 1（37 秒），21 tests / 3 failures。ThemeSwitch 4/4全过；Accessibility 3/5，真实 root 坐标无重叠断言已过，剩标签和placeholder的文字宽度overflow（自然宽度舍入）已改为占据真实可用宽度；Guard仍同端点例外计数，已通知owner。
- 第五批 `tests-pilot-5.log`：exit 1（42 秒），23 tests / 1 failure。Guard 12/12、ThemeSwitch 5/5（新增真实渲染玻璃/错误像素对比通过）、大字体交互原5项全过；唯一失败为新jump测试click后未等待滚动动画完成，正在定向复验。旧guard/标签/placeholder失败均已清除。
- 第六批 `tests-pilot-6.log`：测试编译 exit 1（10秒），新 NOIR mention 场景缺失必传 fixture `image` 参数；已明确传null，不涉及产品失败。
- 第七批 `tests-pilot-7.log`：exit 1（24秒），31 tests / 1 failure。Guard12、ThemeSwitch6、SemanticColors4、Highlight3均通过；Accessibility原5项通过，jump新增1项失败已定位：正文按段落生产拆分，测试错误地查找跨段整串，现定位真实首段文本，保持真实jump操作与边界断言。
- 第八批 `tests-pilot-8.log`：exit 0 / BUILD SUCCESSFUL（28秒），14 tests / 0 failures / 0 skipped。`PilotAccessibilityTest.onlineJumpActions_scrollToRealFirstAndLastMessages_withAccessibleTargets`、`RoleplayThemeSwitchUiTest.noirMentionPanelOnLightTheme_usesMaterialContentAndSelectionCallback` 各1通过，`UiTokenSourceGuardTest` 12/12通过；生产与测试源码均重新编译。加上第七批其余通过结果，31个定向用例均已有通过证据；最终整批门禁由主会话执行。

## 已取得的行为证据

- P01：320×640 / 200%字体下四Tab的Role.Tab和selected、48dp与stage边界、文字无裁切、切换与新增角色/会话回调。
- P02：真实SettingsViewModel上的搜索、无结果、角色选择、更多菜单，48dp边界。
- P03：真实header/list/input的root坐标不相交、首消息低于头部、placeholder不overflow，输入发送、返回、设置、快捷面板滚动到钱包并触发回调；停止生成两入口；AI帮写主按钮展开后48dp；跳顶/底到真实首段。
- 真实P03同一Composition切换浅/深应用theme，用户气泡文字重新取role且几何不变；固定动态scheme确实传入真实消息页面。
- `ImmersivePaletteCacheKey`真实工厂验证同URI/同尺寸下bitmap generation和identity、完整ColorScheme变化使key不同且不保存Bitmap强引用。未声称已测异步produceState生命周期或真实URI加载。
- 白/灰/黑图像在无scrim与P03三档scrim、reading sheen、正文加深及黑白极端局部背底上的正文/强调色对比至少4.5；真实white-image P03渲染后的bubble像素+文字亦通过，错误气泡保持不透明Material errorContainer。
- NOIR+浅色实际mention面板的title/collapse/candidate内容分别使用Material配对且选择回调正确。
- 13皮肤的品牌/深色阅读面/透明liquid及保存几何原值通过纯解析测试；既有玻璃谓词、存储回读测试交最终整批回归。

## 交接边界

实现代理已冻结产品、截图夹具及JVM测试；主会话/检查代理接管最终Lint、dev构建、AndroidTest编译、整体单测与截图verify。设备AndroidTest由检查代理维护后接主会话编译。未连接设备，不把编译计为设备交互通过。

旧78张PNG未修改；预期的截图像素变化尚待主会话verify产物与人工审阅。默认/CI截图路径、测试ID、像素阈值未改变。没有提交、推送或发包。
