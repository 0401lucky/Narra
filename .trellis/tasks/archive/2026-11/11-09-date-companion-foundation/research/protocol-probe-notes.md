# 协议失败帧定向诊断

- jvm-02：Responses嵌套response.error没有进入旧错误提取器，失败帧可走正常Completed；已修嵌套错误与缺正文的response.failed识别。
- jvm-03：168项中167通过，原三协议错误用例已通过；新最小/格式坏失败帧用例发现流异常跨flowOn后可能丢失缓冲FAILED元事件。
- protocol-probe-01：1项失败。诊断为body="malformed"时异常已经正确抛出，但消费者得到0个结束证据；原XML保存于protocol-probe-01-results。
- protocol-probe-02：新增消费端透明catch及调度回归后，编译因缺少JUnit assertSame import失败，**测试没有执行**，没有本轮XML。已补import。该轮不能拿Gradle目录中残留probe-01 XML当作新结果。
- probe-03 / probe-04：单纯在flowOn后catch再emit/throw仍有尾部丢失竞态；记录已保全，不作为通过依据。协程栈恢复可复制异常对象，消费者测试检查原类型/消息与无失败重标，不要求引用恒等。
- probe-05：21/21通过，含40轮跨dispatcher错误交付与消费者异常/取消、三协议实际HTTP及最小失败事件。
- 最终策略：在跨dispatcher之前把上游异常封成内部终止数据，缓冲正常排空；消费端先交付一次FAILED证据，再抛回原异常。Cancellation仍直接传播，消费者异常不被重新标为上游失败；不添加Completed、不复制错误正文到元数据。正式冻结验证另记。
