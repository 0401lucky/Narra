# D4 审查短入口

首次原生check在大注入下实际TPM失败，未记为通过。本入口减少首轮重复注入；
完整14条清单保存在 check-full-context-continue-2026-11-18.jsonl。要求没有降低。
从backend index的Quality Check加载实际相关规范与完整D4 PRD/design/implement；
已注入正文不要重复整读，源码/长日志按具体符号或相关区段加载，避免一次输出无关历史。

版本2.4.1，既有D4 in_progress。只审D4增量及其真实依赖，不重做v2.4记忆或D1–D3。
本次起点1425源码/281保护项见continue-2026-11-18/baseline；
全部本次构建命令、前后源哈希与原始XML见research/resume-2026-11-18。

核心：纯Player唯一generation/时钟，完整grapheme（包括GB9c）；
Scene/角色外观优先级、stable refs、CAS/高版本/真实文件与共享引用；
独立Reader scope按角色/persona/space/run/purpose和来源筛选，SQL BINARY UTF-8
排序与模型比较一致、keyset删游标、刷新保展开页、锚点与跨源选区；
原Screen消息viewport slot、原输入/快捷/回调与默认列表、HUD当前gate/历史label；
320dp/200%/48dp/IME/Back、同指针长按拖动取消、图片真实解码/重排、
显示不写canonical/source revision或调用主文本模型。D5只接本地SpeechUnit选区接口。

已有27 Player方法及48组合JVM方法实测通过；这两组有重叠，不累加为75。
UI/导航初稿已写。首次UI receiver错误已修；AndroidTest单行import已修，
后续Room/资源/VM全覆盖和设备真实结果以最新交接与报告为准，不从本入口推定通过。

主会话唯一Gradle/设备/formatter；check不并行运行构建。主会话明确冻结并移交
源码后再自修已确认问题，否则先只读报告，避免与integration/native_tests交叉写入。
主会话拥有父/版本/spec，check可写本D4审查记录。

保留12个既有UI漂移：唯一明确允许的RoleplayScreen.kt最小adapter为默认null
display参数/两处透传和Stage HUD抑重复gate；其它旧UI、schema、PNG、inventory、
版本/更新JSON不重置或record。不提交/推送/发布、不碰用户真实数据或调用付费模型/TTS。
未验证、编译/测试失败、旧像素差异与新候选待人工接纳分开记录。
