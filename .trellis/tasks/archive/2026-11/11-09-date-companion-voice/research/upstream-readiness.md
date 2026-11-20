# D5 现有声音接口预核对

2026-11-11，D3实施期间只读核对；D5尚未启动。D3/D4最终投影、选区与生命周期接口仍须在依赖验收后补入。本记录只描述现有源码事实及已批准设计的接线注意点，没有调用TTS或验证真实音色。

以下路径相对 `app/src/main/java/com/example/myapplication/`。

## 真实开关与服务

- `model/VoiceSynthesisSettings.kt` 的 `hasUsableCredential()` 同时要求旧 `enabled` 和非空Key。该enabled属于已有语音消息后台合成。D5独立陪伴开关不能通过写旧enabled来开启，也不能无意复用此helper而把陪伴点播绑到后台合成开关。
- `profileForAssistant`解析角色继承和显式DISABLED；后者须单独守卫。`VoiceProfile.ttsModel()`按PRESET/VOICE_DESIGN/VOICE_CLONE选择三个已有MiMo模型，不能只用一个默认model构造所有缓存键。
- `data/repository/tts/MimoTtsClient.kt` 的 `synthesize(apiKey, request, baseUrl)`自行检查Key和非空台词，执行安全端点规范化。底层已支持取消Call与有界完整响应，没有PCM增量结果。
- `MimoTtsRequest.preset/voiceDesign/voiceClone`实际发送不同messages和audio.voice；voiceDesign包含设计prompt，voiceClone包含样本Data URI和instruction。缓存须按最终有效内容建立，克隆路径/文件名/size不能替代样本内容hash。
- 当前 `MimoTtsAudio`只有format与可选voice，没有voice_emotion或language字段。D3投影的视觉/自由emotion/声音emotion保持独立；仅使用现有MiMo支持的参数，不能把未支持标签当服务端效果已验证。

## 文件、播放与取消

- `data/repository/AudioFileStorage.kt`校验WAV/MP3头并限制Base64长度，但 `writeAudioBytes`直接写最终文件；没有D5要求的PENDING/READY、hash回执、临时文件发布或收藏引用保护。D5在复用校验的同时建立自己的资源恢复边界，旧历史音频的写入/读取语义保持。
- `ui/component/roleplay/RoleplayVoicePlaybackController.kt`支持prepared后才start、activeSession/释放标志、停止和dispose；其播放器port当前位于UI包。D5会话协调层如复用底层，须保持依赖方向，不能让ViewModel/Repository直接依赖UI实现。
- 新owner要覆盖翻译、合成、文件结果和播放器整个链条；单卡controller的session保护不等于跨台词/角色/模式/离场的统一代次。以真实取消与迟到prepared/网络回调证明旧请求不能抢播。
- 缓存与收藏是资源引用；收藏已有有效音频不调用模型。取消收藏、清缓存、删源与播放历史音频分别处理，清理不得删除仍被收藏引用的文件。

## 必需交付证据

1. D3/D4同源SpeechUnit与跨场选区映射，不取当前场次或UI窗口代替用户所选范围。
2. 最终HTTP/缓存键矩阵：语种/翻译版本、规范化端点、实际模型、预置/设计/克隆内容、实际朗读文本及服务支持的声学参数；无明文Key。
3. 真实文件+Room故障注入：生成后写文件、文件后挂接、重开补保存、非法头/残缺文件、来源变更、LRU/清缓存与收藏引用竞争。
4. 默认关闭、缺配置、取消与错误保持正文；准备中不报告播放成功；长文分段停止不继续队列。
5. 沿用既有MiMo供应商，新增声音表按当时最新schema追加，旧schema逐字节保护。真实设备音频与模型质量未执行时独立记录。

详细验收仍以D5 PRD/design/implement和父设计§8为准。本预核对不代替原生音频或服务验证。
