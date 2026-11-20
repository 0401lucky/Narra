# Bug Analysis: 空闲通知中的下一动作被旧任务拦截

## 1. Root Cause Category

- E（隐含调度假设）与 D（覆盖缺口）：旧实现先发 `isBusy=false` 再清操作 Job，
  假定 StateFlow 收集者稍后才运行。同步通知能立即触发下一动作，旧 Job 仍活跃时
  会将它丢弃；同步 launch 返回的旧 handle 也可能覆盖新操作。
- 两测试类/fixture/生产四文件与旧交付 SHA 一致；云端 2956 方法中的 12 个失败
  集中于开场和待保存恢复。40 轮本地 2.4.1 参数的原 21 方法通过，不支持版本改动
  或源码漂移假设。41 的同步通知回归明确得到 expected ACTIVE / actual PREPARE。

## 2. Why Fixes Failed

没有反复试补或放宽测试。先保留旧实现取得确定性红灯，再修操作所有权顺序。
不将包装层 packageDebug 错误归因内存、签名或本竞态。

## 3. Prevention Mechanisms

| 优先级 | 机制 | 实际动作 | 状态 |
| --- | --- | --- | --- |
| P0 | 结构 | LAZY 先登记、非空守卫、代次及 Job 身份检查、先释放再通知空闲 | 已修 |
| P0 | 回归 | 空闲通知内立即调用真实 DIRECT，核对 UI/Room、零 HTTP、无消息/turn | 红绿已验 |
| P1 | 规范 | companion-entry-context 的操作所有权合同及错误/测试矩阵 | 已更新 |

## 4. Systematic Expansion

同一 runOperation 覆盖准备、开场、接受、生成与补保存，refresh 必须沿用已登记守卫。
此修复只改该 ViewModel 与一条新增测试；原 21 方法、等待策略、来源/HTTP断言与 fixture
保留，没有扩散为其它 ViewModel 的无证据重构。CI补充失败 XML/报告以保留可判别证据。

## 5. Knowledge Capture

- 41 单条红灯：sourceStable=true，1 方法失败，原生产 SHA 保持。
- 42 两类绿灯：sourceStable=true，22/22，通过，0失败/跳过。
- 规范已落入 `.trellis/spec/backend/companion-entry-context-guidelines.md`，随修复提交。
- 本 Android 仓库没有 `src/templates/markdown/spec` 模板目录，不创建无关模板副本。
