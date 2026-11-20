# D6 实际前置核实

2026-11-19，主会话只读核实后激活原 D6 任务；沿用已批准的 v2.4.1 方案。

- D1–D4 的最新交接分别为 foundation/research/handoff.md、entry-context/research/handoff.md、style-observation/research/handoff-2026-11-18.md、stage-reading/research/handoff-2026-11-19.md。工程前置通过；真实模型效果、物理设备和旧视觉接纳单列。
- D5 的实际任务状态为 completed / engineering_complete，交接为 [handoff.md](../../11-09-date-companion-voice/research/handoff.md)。读取 05/07/09 原始 JVM XML，按 class+method 取最新，重建为 107 passed、0 failed、0 skipped；10-native 实际 8/8，runner 完成且源码稳定。
- D5 最后 1491 项源码清单逐文件重新计算 SHA-256；仅版本功能记录文档的收尾变化，与原 check-final-validation-binding.json 一致，没有工程源码漂移。
- 当前真实数据库是 `data.local.chat.ChatDatabase`，CURRENT_VERSION=56，58 表。55 schema SHA-256 为 `5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033`；56 schema SHA-256 为 `d5e2dff049818be64c7ab4b3bf2b767b99f1bc0546756f10a333ca05e903eab7`，identity=`37d3992a3b6f8544d71ead1e129bccef`。
- 当前 dev APK SHA-256 为 `2c33b68226a4ed1f27cdd60d1af0d13aaa91ee805ef550dc3875ca0ed0527b83`，test APK 为 `b48cc27bf46f1b7db255691f3969a36df1f5499b10cdb33c59f962044853c92b`，均与 D5 最终交接一致。
- `task.py validate` 实际通过，implement/check 各 16 个有效引用；随后 `task.py start .trellis/tasks/11-09-date-companion-history-integration` 成功，将 planning 改为 in_progress。

起点见 [源码清单](validation/initial-source.json)、[保护清单](validation/initial-protected.json) 和 [工作区状态](validation/initial-git-status.txt)：1491 项源码、296 项旧 schema/PNG/发包参数/更新清单等保护项。17 个共享源码的起点内容另存 initial-shared，用于辨认本块实际修改。

D3/D4 的 34 项旧截图差异保持历史记录；D4 actual 与 D3 相同不等于旧基准已接纳。D6 只记录实际新候选与新差异，不 record、不替换旧 PNG。本次没有提交、推送、发布、版本参数/更新 JSON 变更或自动归档。
