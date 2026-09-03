# 思考指南

> 用于在改代码前检查复用、边界、状态和验证范围。具体编码规范以
> `../backend/index.md` 为入口。

## 可用指南

- [代码复用思考指南](./code-reuse-thinking-guide.md)：新增组件、Support、
  Coordinator、常量、解析器或配置前使用。
- [跨层思考指南](./cross-layer-thinking-guide.md)：功能跨越 UI、ViewModel、
  Repository、Room、网络或 Android 系统能力时使用。

## 触发条件

以下任一情况先读代码复用指南：

- 准备新增 Helper、扩展函数、通用 Composable 或 ViewModel Factory。
- 相似状态更新、JSON 解析、URL 拼接、错误映射出现第二次或第三次。
- 修改路由、Provider 配置、消息状态、数据库字段等共享契约。
- 准备复制已有功能再改少量参数。

以下任一情况先读跨层指南：

- 数据从 Compose 经过 ViewModel、Repository 到 Room/Retrofit。
- 新增或修改 Room Entity、迁移、DataStore/Keystore 字段。
- 新增 Provider 协议、SSE 事件、更新下载、TTS、屏幕翻译或文件能力。
- 一次性消息、取消、失败重试或敏感信息需要跨层传播。

## 修改前强制规则

修改任何共享值或契约前先搜索：

```powershell
rg -n "要修改的符号或值" app/src app/schemas docs
```

需要同时确认：定义位置、所有读取方、序列化/持久化、测试、迁移、导航参数、
更新文档和构建配置。不要只改最先看到的一处。

## 审查 AI 结论

每个高风险结论都要回到真实代码验证：

- 数据究竟来自用户、远端 API、内部数据库还是打包资源。
- 失败是否真的会泄漏到 UI 或日志。
- 代码注释描述的是兼容约束还是待删除旧逻辑。
- 测试是否真正驱动被测行为，而不是删除实现后仍会通过。

规范来自当前仓库事实，不使用通用 Android 教条替代源码证据。
