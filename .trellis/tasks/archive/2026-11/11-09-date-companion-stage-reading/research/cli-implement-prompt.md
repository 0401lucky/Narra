Active task: .trellis/tasks/11-09-date-companion-stage-reading

你是 Trellis implement 子代理，直接完整实施本块，不派发其它代理。用户已
明确批准完整v2.4.1 D1–D6并恢复剩余实施，沿用现有任务，不新建、不重复
确认、不受旧会话收口限制。主会话现已激活D4，planning→in_progress。

先读 AGENTS、此任务 implement.jsonl 全部引用、prd/design/implement、
以及 D3 research/handoff-2026-11-18.md。D3当前必要验证：JVM去重37/37、
设备去重37/37、联合编译/Lint/双APK通过；观测平台/布局/实际文本fontScale
均实测2.0且窗口恢复。旧35截图有34项既有UI像素差异，候选保留未接纳，
不能声称截图绿测，也不能阻止已批准D4代码实施。源码所有权已释放。

完整D4责任：可注入时钟的纯本地BeatPlayer（打字/补全/下一拍/循环重播，
grapheme完整、取消旧timer、源版本/离场失效）；真实线下Stage/列表/阅读
切换、输入/继续/重生成保留，显示操作零主文本请求；角色立绘/表情图集/
套装/背景/布局草稿和CAS，确定目标表情→套默认→旧场景图→头像→姓名
回退，DECOR不作主体且共享素材不误删；独立授权跨场Reader keyset分页/
121+混合来源/删游标续页/去重/刷新保展开页/滚动锚点（新答/字体/图片/IME/
前插/删除），阅读不enterScenario旧场或改变当前重生成目标；48dp、320dp/
200%、长按滚动取消、覆盖层/系统Back/IME。D5需要稳定选区/同源朗读单位
接口，但本块不接真实网络声音。

文件所有权：你负责新增CompanionStage/Player/Reading/Resources领域类型、
服务/仓储/ViewModel/UI/相应JVM及AndroidTest；串行接手所需Companion设置
model/codec/Store patch、ConversationDao/读取仓储、RoleplayNavGraph/
CompanionRouteContent/RoleplayScreenContent/DialoguePanel/ReadingMode/
Portrait和AppGraph必要adapter。主会话拥有父任务/版本/spec与验收矩阵。
先尽量避开已有UI agent的12漂移文件（D3 implementation-build-01-source-drift.json），
特别RoleplayScreen.kt/ImmersivePhoneShell.kt；若不可避免，先给主会话最小
共享hunk和原因，不回滚其UI设计。你不是代码库唯一执行者，保留v2.4、
用户与他人全部改动，禁止重置/覆盖无关内容、基准/inventory或旧schema。

主会话是唯一Gradle/formatter/设备执行者。你不得运行Gradle或设备；源码
完成后明确冻结/释放并给出最小实际测试选择器、编译/Lint和native用例数。
主会话执行后把真实失败交你修复，直到本块可独立验收。测试必须有实际
成功/异常/状态切换、真实Room/keyset/文件/回调证据，不只字符串或镜像实现。
合成数据/隔离环境，不调用付费文本模型/TTS；截图只verify，候选不替换基准。

保持schema55（本块查询/配置不需要新Room实体时不要加表）；后续D5负责
声音迁移。功能固定2.4.1，不改构建版本参数/更新JSON，不提交/推送/发布。
每阶段及时写本任务research/implementation-handoff.md及真实实施清单，区分
已实现/已验证/待验证，不把研究当交付。继续完整D4，遇 routine 技术选择
自主按既定合同处理；缺失产品批准范围不存在，不重新询问用户。

注意当前worker沙箱可能不能直接写用户目录下的Trellis channel锁。最终
回复会由runtime自动路由；可通过最后回复报告，不要重复尝试权限扩大。
