# D6 资料与完整读取接口预核对

2026-11-11，D3实施期间只读核对。D6仍等待D1–D5最终验收；本文不能代替届时的实际历史/声音/资源接口与工程证据。

以下源码路径相对 `app/src/main/java/com/example/myapplication/`。

## 现有资料链

- `model/ContextDataBundle.kt`的DTO默认version为1；`viewmodel/ContextTransferViewModel.buildCurrentBundle()`实际导出version=2并含memoryArchive。不能只看DTO默认值推断当前文件协议。
- `data/repository/context/ContextTransferCodec.kt`目前只接受1–2，显式限制memoryArchive属于v2，并重新构造返回DTO。新增陪伴域须同步decode返回、版本校验、normalize/filter/count/isEmpty/preview/confirm，不能只给data class增加属性。
- `ContextTransferViewModel.mergeImportedData()`当前先执行外层 `dataImportTransaction`，将记忆/世界书/预设置于Room事务；提交后notifyImportCommitted，再调用settingsEditor.saveAssistants。陪伴设置CAS与资源恢复应在此真实流程接入，保持v2.4外层回滚/通知边界。
- 当前pendingMemoryPlan只表示记忆导入计划。D6需明确整个资料操作的Room/设置/文件阶段回执；Room已完成而设置失败后，重试不能重新覆盖用户后来编辑的Room对象或新设置。没有记忆域的陪伴资料也须有自己的可重试计划。
- 新陪伴域缺失与显式更新不同：旧包缺域保持目标角色现值。完整角色列表保存不能覆盖D3/D4/D5的独立配置映射，目标ID映射、角色删除及高版本保护须按实际设置服务处理。

## 三种输出分别验证

1. **TXT阅读副本**：从D4完整授权仓储遍历，按本场/日期/角色范围重新取源，保留原文、时间、说话人和typed控制行；UI只加载50项时仍导出121+项。原文标签不能被模型摘要Formatter或播音过滤规则吞掉。
2. **公开装扮包**：独立白名单，只包含获准外观配置及内容映射素材；不能直接序列化整份CompanionCharacterSettings，也不能把私有路径、补充prompt、原文/记忆、Key、音色样本或高版本未知字段一起带出。
3. **私人ContextDataBundle**：继续保留v2.4已授权记忆/来源语义快照，按既有目标与出处规则恢复；增加陪伴偏好不会把资料库变成整个聊天或声音收藏的恢复备份。

## 最终集成核对

- D1–D5最终handoff逐个绑定实际源码/测试，44+8验收矩阵逐项给出结果及限制。
- 真实AppGraph/原入口→新场→输入/继续→原线上返回的接线故事，区别于此前合成NavHost的局部控件测试。
- 原文修订使旧投影/播放选择/源依赖失效，人工独立记忆与声音收藏继续保留；回看旧档不重绑当前运行。
- SAF取消/IO失败、资料阶段失败/重试/CAS、共享素材与声音资源清理须有实际行为证据。
- 完整JVM、受影响截图verify、Lint、dev/AndroidTest双APK与必要隔离设备验证。旧基准未获接纳不替换，用户v2.4待收尾事项不擅自更改状态。
- 没有新增定位或PCM增量声音；真实模型、物理设备音频与发布按实际执行如实记录。

本次仅做源码核对，没有生成或导入任何用户资料，没有改发布参数或运行验证。
