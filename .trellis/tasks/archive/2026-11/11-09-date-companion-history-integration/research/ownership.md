# D6 文件所有权与验证权

原生 Trellis 实施代理由主会话派发，不再嵌套派发。每个代理先加载本任务 JSONL 与实际交接；共享文件交接后串行写入。所有执行者保留已有用户/其它任务修改。

| 执行者 | 所有权 |
| --- | --- |
| d6_history | D4 阅读仓储/授权查询扩展、新历史管理模型/仓储/ViewModel、D1 CAS 批次适配、TXT 与 SAF 数据适配及对应领域测试 |
| d6_transfer | ContextDataBundle/codec/transfer ViewModel、公开装扮与私人配置域、设置/资源必要适配、冻结计划与回执；确需扩表时唯一负责 56→57/注册/固定56/S3守卫与领域测试 |
| d6_ui | AppGraph/AppRoutes/RoleplayNavGraph/CompanionRouteContent、历史/装扮 Screen、Reader 入口与定位、ContextTransferScreen、最小角色/线上入口 adapter 和真实集成故事测试 |
| 主会话 | 规范、版本功能记录、任务树/44+8证据账本、验证记录脚本；唯一运行 Gradle、IDE formatter、截图 verify 和设备；代码冻结后派原生 trellis-check |

验证只使用合成数据、Fake、MockWebServer 与隔离库/合成 AVD。阶段验证只覆盖相关变化；冻结后一次完整 JVM 与受影响截图/Lint/编译/双 APK/必要故事，失败修复后定向复验。没有新代码或新失败不重复全套。

本轮不操作真实用户库、凭据或全局配置，不改发包参数或更新 JSON，不提交、推送、发布或归档。TXT 是阅读副本，私人资料不升级为全聊天/音频备份，跨存储分别记录阶段回执，不声称原子提交。
