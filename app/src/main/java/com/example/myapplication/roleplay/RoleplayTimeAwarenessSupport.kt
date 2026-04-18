package com.example.myapplication.roleplay

import com.example.myapplication.model.RoleplayInteractionMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 场景级时间感知提示词支持。
 * 线上模式强调即时聊天与失联后的时间后效；
 * 线下模式只强调真实日期、昼夜状态和行为合理性，不主动制造时间断层。
 */
object RoleplayTimeAwarenessSupport {
    fun buildPromptSection(
        interactionMode: RoleplayInteractionMode,
        nowProvider: () -> Long = { System.currentTimeMillis() },
    ): String {
        val currentTime = formatCurrentPromptTime(nowProvider())
        return when (interactionMode) {
            RoleplayInteractionMode.ONLINE_PHONE -> {
                buildString {
                    append("【线上模式设定】\n")
                    append("1. 当前绝对时间：")
                    append(currentTime)
                    append("。\n")
                    append("【时间感知规则】\n")
                    append("- 你必须结合当前时间点主动调整回复状态：深夜应困倦或慵懒，清晨应刚醒或迷糊，工作时段应忙碌或分心。\n")
                    append("- 如果用户在不合理时间做出反常行为，角色应自然质疑，而不是无视。\n")
                    append("- 如果系统注入了【时间旁白】，你必须自然感知时间流逝：可以吐槽对方消失太久、表达想念、假装不在意，或冷淡回应，具体反应取决于角色人设和关系阶段。\n")
                    append("- 禁止变成“催睡打卡机”，不要机械地在每次深夜都提醒睡觉，除非角色本来就是这样。")
                }
            }

            RoleplayInteractionMode.OFFLINE_DIALOGUE,
            RoleplayInteractionMode.OFFLINE_LONGFORM,
            -> {
                buildString {
                    append("【时间感知】\n")
                    append("1. 当前绝对时间：")
                    append(currentTime)
                    append("。\n")
                    append("2. 你必须自然感知真实日期、星期、昼夜和作息状态，让动作、精力和环境氛围符合当前时间。\n")
                    append("3. 如果剧情现场没有明确写出独立的剧内时间锚点，默认把当前真实时间视作当下场景时间。\n")
                    append("4. 如果场景里已经有清晰的剧内时间锚点，优先遵守剧情连续性，不要被真实时钟强行带偏。\n")
                    append("5. 不要为了表现时间感知而强行总结“过去了多久”，更不要无根据地跳时间线。")
                }
            }
        }
    }

    fun formatCurrentPromptTime(timestamp: Long = System.currentTimeMillis()): String {
        return runCatching {
            SimpleDateFormat(
                "yyyy年M月d日 EEEE HH:mm",
                Locale.SIMPLIFIED_CHINESE,
            ).format(Date(timestamp))
        }.getOrDefault("当前时间未知")
    }
}
