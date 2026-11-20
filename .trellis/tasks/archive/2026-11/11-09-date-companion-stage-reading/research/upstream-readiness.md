# D4 阅读与资源接口预核对

2026-11-11，主会话在D3实施期间只读核对。D2已验收，D3尚未交付；本记录不代表D3/D4通过。启动D4时须读取D3最终handoff，以当时源码和已验证投影接口为准。

## 真实入口与范围

以下源码路径相对 `app/src/main/java/com/example/myapplication/`。

| 当前入口 | 核实事实 | D4接线要求 |
| --- | --- | --- |
| `ui/navigation/RoleplayNavGraph.kt` 的 `ROLEPLAY_READING` | 现使用图级RoleplayViewModel的当前 `messages`，路由参数解码后必要时 `enterScenario` | 新陪伴跨场阅读应使用独立的授权读取状态；浏览旧页不切当前场次或重生成目标。旧普通RP/群聊/长文仍沿兼容路径。 |
| `ui/screen/roleplay/RoleplayReadingMode.kt` | 现接收已投影列表、标题、背景、行高、高对比度和onDismiss；列表key为 `uiItemId`，尚无仓储分页契约 | 维持原主题/系统栏；跨场分页、前插与滚动锚点须由稳定源身份协调，不能只在当前列表外包一层“更多”。 |
| `model/RoleplayMessageUiModel.kt` | 有 `sourceMessageId` 与分段 `uiItemId`，当前基础模型没有持久raw或独立sourceRevision | D4使用D3正式投影的源ID/修订/beat身份。多段来自同一原文时，锚点要能映射到实际可见段落；不能把uiItemId当Room消息ID。 |
| `data/local/chat/ConversationDao.kt` | 实际消息DAO在此；`listMessages` / `observeMessages` 为单conversation的createdAt+id全量排序 | 新keyset游标保留排序值+ID，游标行被删除仍可续页；完整读取不能依赖游标行存在或UI已加载集合。 |
| `data/repository/roleplay/CompanionRepository.kt` | `messages(encounterId)` 和 `listEncounters(conversationId)` 仅支持对应局部读取 | 跨场/日期能力须显式扩展合法读取边界，不把单场方法误称角色全历史。 |
| `data/repository/roleplay/CompanionHistoryRepository.kt` | D2 `freeze/read/validate` 的AllowedCompanionHistoryScope面向单次模型授权，有冻结截止和maxMessages | 阅读范围与该请求范围分开；不能通过扩大模型scope来实现历史阅读，也不能从阅读页反推模型授权。 |
| `data/repository/context/MemoryArchiveRepository.kt` | 现有角色/空间/身份管理上下文、listArchiveConversations及基于limit/offset的listArchiveMessages | 可复用归属事实；原管理offset接口不等于已满足D4 keyset、跨会话和删游标合同。不要为新阅读改坏v2.4管理入口。 |

## 资源与旧值

`RoleplayScenario` 已有backgroundUri、双方portraitUri/Url、角色/身份与原显示开关，没有表情套装。`LocalImageStore.copyToAppStorage(uri, scope)`复制到应用私有 `user_images`，返回稳定绝对路径；`deleteIfLocal`仅校验路径属于私有根，**没有跨角色/场景引用计数**。

D4需在调用清理前核对完整引用集合，保留共享素材；新导入尚未挂接的临时文件可按本次所有权清理。未选择新套装沿用旧scenario图；明确选择后按目标表情→套默认→旧图→头像→姓名回退。失效文件、仅DECOR素材、角色删除与保存CAS失败均需实际文件/像素或仓储测试，不能用字符串存在替代有效图像。

## 交付后须补齐

- D3最终ParsedReply/Beat与实际source/config版本字段、历史投影查询接口。
- D4本地player的唯一代次/时钟所有者；切视图、重播、HUD和阅读均只做本地动作。
- 121+混合来源的真实分页与删游标行为；上翻/新答/字体/图片/IME/前插/删除的锚点与底部跟随。
- 真实Screen/NavHost、320dp/200%/48dp和原主题截图验证；设备与真实服务效果分别记账。

本次未改产品代码、启动Gradle或执行设备；D4仍等待D3工程验收。
