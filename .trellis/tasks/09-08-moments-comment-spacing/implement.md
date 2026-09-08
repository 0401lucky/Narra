# 评论间距执行计划

## 前提与所有权

本任务不依赖气泡开关子任务。父任务完整方案获批且本任务启动后，由 Trellis 实现 / 检查子代理处理。所有权仅限 MomentsScreen.kt 的评论预览布局和 MomentsScreenTest.kt 相关用例。工作区存在其他编辑，不得回滚他人改动，不修改 RP 主题、共享尺寸值或版本文档。

## 执行清单

1. 在 MomentEngagementBlock 中去掉评论预览的显式 48dp 最小高度，将行内上下间距设为 2dp、容器条目间距设为 4dp。
2. 保留所有评论及按钮回调、语义标识、触感反馈和原有文字限制。
3. 更新唯一固定评论预览大行高的旧断言，补充四条短评论的密度、长评论 / 大字体展示、相邻评论各自命中及单击打开详情的验证。
4. 用父任务统一的 AndroidTest 编译步骤检查测试代码；在可用设备执行 MomentsScreenTest 并复核实际评论块画面。
5. 检查差异仅涉及预览和定向测试，再由检查代理复核父任务 A5、A6、A7。

## 验证边界

设备执行：.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.ui.screen.moments.MomentsScreenTest --console=plain

全量 JVM、Lint、开发包及 AndroidTest 源码编译由父任务集中执行。此项不改业务逻辑，不添加只复述尺寸常量的 JVM 测试。设备未连接时如实记录未执行，不把旧测试通过或编译通过当成当前视觉已验证。

## 交付与回退

向父任务交付局部差异、实际执行命令和结果。任何视觉或手势回归在本任务内修复并复验；必要时仅还原所属局部修改，不清理用户数据或回退整页。

执行结果：局部布局及三项新设备用例已完成，MomentsScreenTest 纳入通过的 18 项设备测试；紧凑与大字体截图复核通过。完整结果见 ../09-08-roleplay-moments-ui-feedback/verification.md。
