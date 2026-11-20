# D2 作者实施交接

版本固定 2.4.1；承接会话 `01a1c5ad-b902-7de0-995f-296d4271b585`。本块未提交、推送或发布，保留已有 v2.4/D1 及无关工作区改动。D3–D6仍按父任务实施。

状态：作者实现和验证通过，所有构建/formatter均已结束；生产/测试和Gradle所有权已释放给主会话/checker。独立检查及原生设备执行由主会话继续。

精确范围为44个Kotlin文件，清单见 `implementation-files.json`。冻结1392项源码/参考及273项保护资料均无漂移，完整结果见 `implementation-validation.json`。

## 实际实现

- 真实 RoleplayNavGraph 调用 `CompanionRouteContent`，支持显式新场和无 entryId 的 DRAFT/PREVIEW 恢复。设置/阅读/ON_RESUME仅读状态，ON_PAUSE取消当前操作。
- 两种生成开场只有预览，接受事务后才成为 canonical；直接开始不请求、不 seed greeting。保留旧会话原 greeting、run 和 memoryPurpose。
- `RoomCompanionEntryState` 在无请求准备 attempt 中保存 `entrySelectionId`，选择时比较旧选中 ID，同场景 Room 事务更新标记及场次。DIRECT 自身无 OPENING turn；重建 VM/关库重开均可恢复原线上 origin。
- `CompanionRequestCoordinator` 每次显式请求重新准备世界书/记忆和授权原文；首次开场冻结点击时的新鲜历史，后续重生成沿原 cutoff/授权消息集合。CONTINUE 使用持久类型，不把技术长文写入原文或来源。
- `RoomCompanionHistoryRepository` 只聚合同角色/persona的允许单聊绑定；独立 run、群私有、空授权、断点、排除、waiting 和失效来源不可旁路进入最终 HTTP。
- `CompanionRepository.validateRequest` 在 Room 事务复用 claim/turn/输入/引用/运行守卫。三协议在回执准备之后、实际发送之前再次复核；仅调用发送入口才标记 sending，拦截用 SOURCE_CHANGED，历史 SENT 传输证据保留。
- 时间使用 `CompanionTimePolicy`。发送前改时区版本或场景时间开关拒绝旧请求；已发出后继续按原快照记录结果。关闭现实时间不改变叙事日期或原文时间戳。
- 陪伴设置使用独立 DataStore 小映射和 revision CAS，保留未知字段/高版本；旧整角色保存不覆盖映射，角色删除同次 edit 清理其条目。
- `CompanionPromptSupport.adaptBuiltInPreset` 仅按已知 builtin ID、entry ID 和出厂原文一致性替换强推块；只在 `assembleCompanion` 使用，不改数据库预设、用户写过的块、Sampler或槽顺序。`includeScenarioPersona=true` 把陪伴有效身份注入既有 persona 槽，旧路径仍由 Decorator 注入。

## D3/D6 接口注意事项

1. D3继续扩展 `companionCharacterSettingsJson`、`updateCompanionSettings` 和 `CompanionRequestDescriptor`。现在的 `settingsRevision + timePolicy` 不是完整文风/观测配置快照；D3仍须保存实际生成配置和稳定观测字段定义，旧历史不能读取当前设置解释。
2. 新的 `CompanionSessionPreparation.opening` 可为 null（例如已选 DIRECT）；使用返回的实际 encounter，不能假定原 entryId 就是当前场次。
3. 准备标记位于 `entryId:opening` 的初始、未发送且无 artifact 的 attempt；它可随候选淘汰成为 CANCELLED，但仍承载 entrySelectionId/origin。它不是正式经历、用户控制行或可读正文。D6按 committed canonical 查询历史，不把这些空准备根显示为见面记录。
4. 标记随原 conversation/encounter/turn 级联清理；删除场景已覆盖完整关联清理和另一场景保留。删除单条正文不应擦掉还在使用的入口返回元数据。不存在独立的陪伴原文数据库。
5. 免费补保存、接受、重新请求是不同按钮/接口；NOT_STARTED INPUT 恢复只显示显式请求动作，不自动调用模型。失败请求和旧产物不可通过回退清空当前答案恢复。
6. 新 persona 参数默认 false，保持所有旧调用行为；仅 companion 请求传 true。未改写的内置块适配已解决默认 G01 冲突；五文风/人称/协议/观测仍属于 D3。

## 需求与行为证据

| 范围 | 已有证据 |
| --- | --- |
| D2-AC1 三开场/旧恢复/接受 | CompanionEntryRepositoryTest、CompanionEntryViewModelTest、CompanionRequestCoordinatorTest；预览/废弃零 canonical，直接开始零请求，接受失败/连点幂等 |
| D2-AC2 导航/迟到/原路返回 | VM恢复和真实Room选择/返回；两类原生 Entry/Navigation 测试已编译，设备执行待主会话 |
| D2-AC3 INPUT/CONTINUE/重试/重生成 | 三协议实际 HTTP、存储控制行、跨模式投影、旧答ID/时间保持；真实默认/Qwen及旧模式/自定义预设 HTTP |
| D2-AC4 授权范围 | Room范围矩阵及最终 HTTP，空允许集合/断点/群私有/独立/角色/persona；传输准备期间删源、改输入和切模式零发送 |
| D2-AC5 召回/世界书/时间 | 本请求记忆关闭/失败、depth 0真实role/order、参数与回执指纹；四项时间测试及真实文件DataStore兼容/CAS |

## 验证与限制

- 联合 JVM 361 项和后续预设/身份 80 项均通过；按 class+test合并为27类、385个唯一用例（新增D2为54项），不是单次385项运行。原始XML分别在 `implementation-jvm-results/` 和 `implementation-preset-results/`。
- 最后语义变更之后进行了定向回归；IDE仅格式化本块文件，随后重新编译、Lint、构建和截图验证。冻结源及最终验证汇总见 `implementation-final-source-hashes.json` / `implementation-validation.json`。
- 截图使用 verify，E04 14、H01 18、H02 3，共35项；没有 record、接纳候选或改旧PNG。
- `CompanionEntryScreenTest` 10项、`CompanionEntryNavigationTest` 3项由独立UI实现代理提供并已通过源码编译。它们使用实际生产页面和 NavHost wrapper，不等同于整个 AppGraph 启动。设备/IME/真实像素执行仍交主会话。
- 所有请求测试使用 loopback MockWebServer、合成数据和隔离 Room/DataStore；未调用真实模型、TTS或操作真实用户数据库。模型表达质量、语音效果和物理设备体验不由这些测试推定通过。
- 数据库保持 schema 55；发版参数仍是当前仓库的 2.3.0-dev/20300，APK只是本次代码的本地验证产物，不是2.4.1发布包。

## 最终构建产物

- Debug APK：`app/build/outputs/apk/debug/Narra-v2.3.0-dev-20300-dev.apk`，SHA-256 `94febda00d310244663df544e0fbdacbce912825e257404d5fa6e2a1c45d3d06`。
- 测试 APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，SHA-256 `730fe6feb1923159d78f1d4cac1efddd6ec9472e6cee451b4b7ada8b980ab50f`。
- 两包签名校验通过，证书SHA-256同为 `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`。
- 最终 Lint 通过（0 errors，54条告警），Baseline与AndroidTest编译、双APK和35项截图verify通过。原始最终日志为 `implementation-final-02.log`。
