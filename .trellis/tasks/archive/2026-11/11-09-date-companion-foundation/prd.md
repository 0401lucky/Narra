# v2.4.1 陪伴 D1：场次与可靠回合基础

## 目标与归属

为新单角色陪伴提供可追踪场次、语义回合、生成产物与安全提交，保存失败/并发/重启不丢旧答、不静默重复收费，继续复用v2.4来源体系。

父任务为 [完整陪伴升级](../11-09-date-companion/prd.md)，版本固定2.4.1，承接 N2、N3完成性、N4来源资格；AC2/AC3/AC6/AC12。用户已确认升级现有线下扮演和完整规划；完整方案已于2026-11-09获实施批准；按前置依赖由主会话激活，当前状态见task.json。

## 前置与边界

前置：父任务最新完整规划已获后续实施批准。父任务的记忆/身份/版本/入口/范围外条款同样适用；依赖不由任务树顺序隐含。

## 需求

- **D1-R1**：建立encounter/turn/attempt与canonical消息关联；场次不等于memory run，旧消息以空link+legacy兼容，保持ID/时间/原文。
- **D1-R2**：把生成是否完整、产物是否已持久化、canonical是否提交分开；已落盘产物可免费补保存，首次全部存储失败的跨进程恢复限制如实表达。
- **D1-R3**：INPUT/CONTINUE/OPENING有类型；来源资格与COMPLETED显示状态分离，pending/control不可通过自动、手动、旧引擎或摘要旁路成为言行。
- **D1-R4**：持久claim/幂等回执及CAS原子替换；生成中保留旧canonical，编辑/删除或绑定变化拒绝迟到结果。
- **D1-R5**：三协议主流提供真实完成证据，新陪伴UNKNOWN/截断/纯推理不发布成功来源；原线上/群/长文/停止/旧回退语义兼容。
- **D1-R6**：迁移从实施时最新schema递增，完整贯穿Entity/model/mapper/资料与固定旧库；不修改已有v2.4原schema或用户数据。

## 验收标准

- [x] **D1-AC1**：两个owner抢同turn只允许一次有效请求/提交；同文新输入可正常形成不同turn。
- [x] **D1-AC2**：注入请求前、artifact写入、canonical事务和回执丢失四类失败；已持久RESULT_READY关库重开补存无新模型调用，旧答保持。
- [x] **D1-AC3**：waiting INPUT即使UI COMPLETED也不能自动/手动/兼容提取；CONTINUE有CONTROL_NOOP覆盖证据且不被completeInput拼回。
- [x] **D1-AC4**：资格提升继承首次persona/可见性；源修订、删除、重开、身份变化时CAS和来源闭包均拒绝旧覆盖。
- [x] **D1-AC5**：三协议完整/未知/截断/过滤/取消有明确结果；新旧入口回归与固定旧库升级通过。

2026-11-10工程验收通过，实际命令、分轮计数及限制见 [交接记录](research/handoff.md)、[独立检查](research/check-final.md) 与 [隔离设备结果](research/device-final-validation.json)。D2 的新产品入口仍由下一块验收。

本块验收对应父 [44+8项矩阵](../11-09-date-companion/acceptance-matrix.md) 的本块行；不能用已有测试文件存在或其它块通过代替本块结果。

## 兼容与范围外

只通过新companion link/契约选择新路径。既有MemoryProcessingJob仍只做记忆加工；旧retry回退功能不替换为新reroll。资料缺失新关联图时保守保留/拒绝机器消费，不剥link伪装legacy。

具体技术合同见 [设计](design.md)，执行、验证和写入边界见 [执行计划](implement.md)。不提交/推送/发布，不重做v2.4记忆或操作用户真实数据；本块完成不等于整个2.4.1已交付。
