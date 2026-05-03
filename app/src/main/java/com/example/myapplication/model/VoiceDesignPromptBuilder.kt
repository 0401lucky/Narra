package com.example.myapplication.model

fun buildVoiceDesignPromptFromAssistant(assistant: Assistant): String {
    val sourceText = listOf(
        assistant.name,
        assistant.description,
        assistant.systemPrompt,
        assistant.scenario,
        assistant.greeting,
        assistant.creatorNotes,
        assistant.tags.joinToString("，"),
        assistant.exampleDialogues.joinToString("\n"),
    ).joinToString("\n")

    val gender = inferVoiceGender(sourceText)
    val age = inferVoiceAge(sourceText)
    val toneTraits = inferVoiceToneTraits(sourceText)
    val pace = inferVoicePace(sourceText)
    val pitch = inferVoicePitch(gender, age)
    val roleName = assistant.name.trim()

    return buildString {
        if (roleName.isNotBlank()) {
            append("贴合“")
            append(roleName.take(18))
            append("”人设的")
        }
        append(age)
        append(gender)
        append("声音，")
        append("声线")
        append(pitch)
        append("，")
        append("语气")
        append(toneTraits)
        append("，")
        append(pace)
        append("，普通话清晰，吐字自然，情绪贴合台词，不夸张，不播音腔。")
    }
}

private fun inferVoiceGender(text: String): String {
    val femaleScore = scoreKeywords(
        text,
        listOf("女", "少女", "姐姐", "妹妹", "妻", "母亲", "妈妈", "小姐", "公主", "女王", "圣女", "女仆", "她"),
    )
    val maleScore = scoreKeywords(
        text,
        listOf("男", "少年", "哥哥", "弟弟", "丈夫", "父亲", "爸爸", "先生", "少爷", "骑士", "王子", "他"),
    )
    return when {
        femaleScore > maleScore -> "女性"
        maleScore > femaleScore -> "男性"
        else -> "中性"
    }
}

private fun inferVoiceAge(text: String): String {
    return when {
        containsAny(text, "儿童", "孩子", "小孩", "幼", "萝莉", "正太") -> "清澈稚嫩的"
        containsAny(text, "少年", "少女", "学生", "青春", "年轻", "元气") -> "年轻"
        containsAny(text, "成熟", "大叔", "长辈", "父亲", "母亲", "老师", "教授", "老板") -> "成熟"
        containsAny(text, "老人", "年长", "爷爷", "奶奶", "沧桑") -> "年长"
        else -> "自然"
    }
}

private fun inferVoiceToneTraits(text: String): String {
    val traits = linkedSetOf<String>()
    if (containsAny(text, "温柔", "体贴", "治愈", "温和", "柔软")) {
        traits += "温柔"
    }
    if (containsAny(text, "冷淡", "高冷", "冷静", "理性", "克制", "沉着")) {
        traits += "冷静克制"
    }
    if (containsAny(text, "活泼", "开朗", "元气", "阳光", "热情")) {
        traits += "明亮活泼"
    }
    if (containsAny(text, "傲娇", "毒舌", "嘴硬", "挑衅")) {
        traits += "带一点傲娇和轻微挑衅"
    }
    if (containsAny(text, "病娇", "偏执", "占有欲", "危险")) {
        traits += "亲近但带压迫感"
    }
    if (containsAny(text, "优雅", "贵族", "从容", "礼貌", "绅士", "大小姐")) {
        traits += "优雅从容"
    }
    if (containsAny(text, "机器人", "AI", "机械", "仿生", "无机质")) {
        traits += "清晰理性，情绪较少"
    }
    if (containsAny(text, "古风", "仙", "剑修", "江湖", "神明", "祭司")) {
        traits += "含蓄有距离感"
    }
    if (containsAny(text, "侦探", "医生", "律师", "教授", "研究员", "军人")) {
        traits += "专业沉稳"
    }
    return traits.take(3).joinToString("、").ifBlank { "自然贴近角色" }
}

private fun inferVoicePace(text: String): String {
    return when {
        containsAny(text, "活泼", "开朗", "元气", "急性子", "话多") -> "语速略快"
        containsAny(text, "冷静", "克制", "沉稳", "优雅", "从容", "慢条斯理") -> "语速中等偏慢"
        else -> "语速中等"
    }
}

private fun inferVoicePitch(
    gender: String,
    age: String,
): String {
    return when {
        age.contains("稚嫩") -> "清澈偏高但不尖锐"
        gender == "女性" && age == "成熟" -> "柔和偏低"
        gender == "女性" -> "清亮柔和"
        gender == "男性" && age == "年轻" -> "清爽中低"
        gender == "男性" -> "低沉稳定"
        else -> "自然有辨识度"
    }
}

private fun scoreKeywords(
    text: String,
    keywords: List<String>,
): Int {
    return keywords.count { keyword -> text.contains(keyword, ignoreCase = true) }
}

private fun containsAny(
    text: String,
    vararg keywords: String,
): Boolean {
    return keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
}
