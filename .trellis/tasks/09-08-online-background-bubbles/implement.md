# 线上气泡执行计划

## 前提与所有权

本任务不依赖朋友圈子任务。必须先取得父任务完整方案的最终批准并启动本子任务；实现和审查使用 Trellis 子代理。执行者只负责本 design.md 列出的主题配置、RP 视图及对应测试，不修改 MomentsScreen、版本文档或另一个子任务产物。工作区有其他编辑，不得回滚他人改动。

## 实现顺序

1. 扩展 RoleplayNoBackgroundSkinSettings 和显式 JSON 解码；确认 missing / null / false / 非法类型的兼容规则。
2. 在现有设置入口添加开关，复用草稿 / 保存链路；保留预设切换和重置动作中的独立开关值。
3. 添加并复用玻璃材质判定；分开线上消息与页面配色，贯通形状、宽度、外壳及文字强调色。
4. 检查所有 hasImage 消费者及默认参数，确认线下及无背景路径仍兼容；不改背景资源状态。
5. 扩展必要测试并执行下列验证；记录实际结果后交给检查代理。

## 必要验证

- RoleplayNoBackgroundSkinSettingsTest：默认开启、normalized 不覆盖 false、其他样式值不丢失。
- AppSettingsStoreTest：旧 JSON 缺失新字段、显式 true / false、null / 非法字段回退、保存并读取 false 后其他字段仍保留；使用现有内存 DataStore 和安全存储 Fake。
- SettingsPersistenceCoordinatorTest：false 经过保存链路不丢失，已有保存失败路径仍正确反馈；不改用无效的仅构造对象断言。
- 对共享视觉判定添加 JVM 组合用例，覆盖线上 / 线下、有 / 无背景和开关两态。
- RoleplaySettingsContentTest：开关读写、预设切换及重置不意外重开；复用真实草稿回调。
- 在 RoleplaySceneContentTest 或同包独立 UI 用例中验证主题气泡的配色 / 宽度 / 材质，以及默认有背景玻璃路径；普通文字、引用、流式和错误态继续可读。

定向 JVM 命令：

.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.model.RoleplayNoBackgroundSkinSettingsTest" --tests "com.example.myapplication.data.local.AppSettingsStoreTest" --tests "com.example.myapplication.viewmodel.SettingsPersistenceCoordinatorTest" --tests "com.example.myapplication.ui.component.roleplay.*" --console=plain

设备测试先由父任务编译，再在可用设备运行设置与气泡相关用例；保存重进和跨场景效果做定向复核。设备不可用时明确标记未执行。

## 审查与交付

核对新增配置的所有读写路径和四个视觉环节，检查没有修改 Room、旧背景值或全应用颜色。通过后提交源码差异及验证记录给父任务；不自行发布版本。发现问题仅回退或修复本任务拥有的文件。

执行结果：源码、配置兼容与保存链路测试已完成，相关模型 / Store / ViewModel / 策略测试纳入通过的 1534 项 JVM 测试；设置与新气泡界面用例纳入通过的 18 项设备测试，视觉复核通过。完整结果见 ../09-08-roleplay-moments-ui-feedback/verification.md。
