Active task: .trellis/tasks/11-09-date-companion-style-observation

你是 Trellis implement worker，当前只读准备下一块D4，不写任何产品代码，
不派发其它代理。用户已批准完整v2.4.1 D1–D6并明确继续，沿用现有任务。
当前主会话活动任务仍是D3，只有观测滚动一项设备失败未通过，Trellis另一个
check worker正在修；D3必要验收后主会话才激活D4并向你移交实施。

目标准备目录 .trellis/tasks/11-09-date-companion-stage-reading：先读其
prd/design/implement/implement.jsonl和相关规范，尤其upstream-readiness、
D3 companion-presentation规范以及当前UI漂移记录。不要读错为D3实施。
唯一可写当前D3的 research/d4-current-ui-seams.md，记录可续接的实际UI
接口、推荐D4文件所有权和需主会话协调的最小adapter。其它均只读。

聚焦当前 RoleplayNavGraph / CompanionRouteContent / RoleplayScreenContent /
Callbacks / DialoguePanel / ReadingMode / Portrait 的真实接口，尽量让新增
本地Stage/跨场Reader/资源设置接线不修改UI agent的12漂移文件，特别
RoleplayScreen.kt/ImmersivePhoneShell.kt。不要重复旧DAO/记忆研究；核对
新UI后的接线位置。Stage必须保留真实输入/继续/重生成、菜单与返回；显示
操作零模型调用，GROUP/长文/旧列表保持，跨场Reader不enterScenario旧场。

你不是代码库唯一执行者，保留全部用户/v2.4/D1–D3/UI改动，不回滚覆盖。
不运行Gradle、formatter、设备，不调真实付费模型/TTS，不新建Trellis任务，
不改版本或更新JSON，不提交/推送/发布。主会话唯一Gradle并维护父/版本/spec。
准备完成在channel报告接口/所有权并等待主会话D4实施指令，不自行提前写代码。
