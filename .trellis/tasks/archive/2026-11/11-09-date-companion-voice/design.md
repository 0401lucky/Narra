# D5 语音与朗读控制：技术设计

以 [本块PRD](prd.md) 与 [父设计](../11-09-date-companion/design.md) 为权威。2026-11-19按批准范围完成工程验收，领域合同与文件所有权见 [接口冻结](research/interface-freeze.md)，实际接口/验证/限制/D6接手见 [交接](research/handoff.md)。

## 依赖输入

[D4](../11-09-date-companion-stage-reading/prd.md) 已通过独立验收并提供对应接口/验证证据。接口差异先回到父设计协调，不在本块建立平行数据或备用状态单例。

## 数据流与合同

父设计§8固定既有MiMo路线。SpeechUnit来自D3/D4源版本投影；VoiceController拥有唯一generation与资源。SpeechRequestKey跨内存/磁盘统一，翻译单独键控。音频以校验过的私有临时文件发布，PENDING/READY资源与Room引用分阶段，不承诺文件/DB原子；引用计数/孤儿清理排除收藏。D5拥有当时最新schema的声音资源/收藏表迁移，D1旧schema不改。当前语言/音色重新合成与播放历史已有音频分开，不静默重写旧产物。

当前冻结接口为 `CompanionVoiceSession`、`CompanionVoiceRepository`、`CompanionVoiceFilePublisher` 和可注入 `CompanionVoicePlayer`；AppGraph提供 `createCompanionVoiceSession(scope)`，UI/ViewModel只消费这些领域ports。声音偏好进入角色小映射的 `voice`，沿用expectedRevision CAS。独立角色声音库不依赖源消息仍存在。翻译旧ABI保持，附加snapshot入口由同一Default实例提供。

实施后合同收口：语种上限共用60；来源观察唯一Job/epoch在下次用户显式播放恢复；停止后无旧请求的PENDING回执仅补保存不恢复队列；取消先持久CANCELLED tombstone再清文件，已取消回执不能补为READY。新freeze拒绝raw/decoded路径含当前Key，不改旧接口。若首次文件/回执或取消标志+清理均未落盘，跨进程恢复不能无条件保证。

## 写入与协调范围

以下源码路径相对app/src/main/java/com/example/myapplication；新类型/目录是计划位置，已有类的详细行号见父research。

- 新CompanionVoiceController/Queue/SpeechUnit适配，RoleplayVoicePlaybackController可复用底层而非多卡并播。
- MimoTtsClient/AiTranslationService/AudioFileStorage受控复用与必要取消/结果接线。
- data/local/companion的声音缓存/资源/收藏实体、仓储、当时最新迁移与AppGraph装配。
- 陪伴声音设置/点播/选区朗读/收藏UI及角色设置map扩展；不改Provider凭据。

你不是唯一在代码库工作的执行者。实施时核对最新差异、保留用户与其它任务改动，所有跨块文件在明确交接后串行写入；不回滚或覆盖他人文件。

## 兼容与失败策略

既有语音消息音频仍是历史产物，改音色不自动覆盖；新陪伴开关不反向开启旧后台合成。MiMo能力之外不假定emotion标签受支持，按现有有效参数清洗/保留，不读出控制标记。

额外费用、旧请求抢播、缓存串音和文件/Room不一致为主要风险。先关闭陪伴播音可回退文字，不删收藏/历史音频；新迁移前向修复。

## 验证设计

新增CompanionVoice/Cache/Favorite/ResourceRecovery测试；Fake音频时钟和MockWebServer证明实际调用数/停止/源版本。必要物理设备测试准备、取消、离场、后台与系统音频行为，未执行明确记录。

每个本块AC都需绑定行为证据；请求计数、源ID/修订、仓储结果、实际UI回调和错误/取消路径优先于实现镜像断言。遵循父计划的合成数据、隔离库及真实效果单独标注规则。

## 上下游交付

输出本块实际接口/文件范围、错误/恢复约定和验证结果；更新本块research中的implementation/check证据供下一块引用。公共contract发生实质变化时同步父/相关子设计，不只通过代理消息约定。
