# 运行时异常、取消与资源边界：技术设计

## 1. 任务所有权模型

可被下一次用户动作替换的任务采用统一规则：

```text
开始新请求
  → 递增 generation / 替换 Job
  → 取消旧 Job
  → 仅当前 identity 可写 loading / result / error
  → finally 只清理自己的句柄
```

- `CancellationException` 在任何宽泛捕获前单独重抛。
- 不以全局 `CoroutineExceptionHandler` 代替用户动作边界的状态恢复。
- 翻译流式 UI 更新留在父 translation Job 内，或携带 run id 后在 Main 合并；旧 run 的
  排队帧直接丢弃。

## 2. Dispatcher 与平台调用

- `DownloadManager.query()` 由可注入 IO dispatcher 包装，ViewModel 只消费领域结果。
- 声音样本校验、读取和 Base64 编码整体在 IO / Default 的明确边界执行；不能只把网络
  请求放到 IO，而把前置读取留在 Main。
- 导出 / 分享的长文本构建根据 Trace 选择 Default，文件写入使用 IO；Activity Result
  回调只启动可取消任务和更新轻量 UI 状态。

## 3. 有界输入

- 声音样本先读取文件元数据并拒绝超限，再分配 `ByteArray`；编码后仍校验协议上限。
- TTS 响应优先检查 Content-Length，并使用有上限读取；缺失或不可信长度时累计字节超过
  上限立即终止。
- Base64 解码前校验规范化字符串的最大长度，避免同时持有多份超大字符串和字节数组。
- 上限常量放在拥有协议职责的模块，测试覆盖 `limit - 1`、`limit`、`limit + 1`。

## 4. 系统资源生命周期

- Image、Bitmap 和投屏资源使用 `try/finally` / `use` 对应生命周期释放。
- `MediaProjectionCaptureEngine` 的原始 Bitmap、裁剪 Bitmap 和 Image 分别明确所有权；
  continuation 取消或 OCR 抛错时也必须回收。
- 组件保持 Manifest disabled；测试可直接构造内部能力，不通过新增产品入口验证。

## 5. 错误与脱敏

- 网络、文件和平台错误在 Coordinator / ViewModel 用户动作边界转换为安全中文文案。
- 不记录响应正文、Base64、密钥、本地路径或完整语音内容，只记录类型、长度和安全状态码。
- 诊断日志失败必须被隔离，不能改变原业务结果。

## 6. 跨子任务文件边界

- `SettingsViewModel.kt` 先由启动设置子任务修改订阅 / 草稿同步区域；本任务随后只处理
  声音样本请求构造和读取 / Base64 区域，必须保留前置改动。
- `RoleplayActionMessageCards.kt`、语音卡动画和 `MediaPlayer.prepareAsync()` 状态机归 RP
  子任务，本任务不修改这些文件或行为。

## 7. 回滚

- 取消修复、dispatcher 迁移、大小门禁和系统资源清理分开提交 / 回滚。
- 若上限误拒绝现有合法语音，回滚上限数值或读取策略，不回退取消和脱敏修复。
- 若系统组件测试需要启用 Manifest，停止并回到规划；本任务明确禁止恢复产品入口。
