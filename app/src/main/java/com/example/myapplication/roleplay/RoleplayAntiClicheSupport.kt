package com.example.myapplication.roleplay

internal object RoleplayAntiClicheSupport {
    private val highRiskClichePhrases = listOf(
        "石子",
        "涟漪",
        "手术刀",
        "刺入",
        "刺穿",
        "祭品",
        "锚点",
        "钥匙",
        "锁链",
        "项圈",
        "枷锁",
        "失而复得",
        "四肢百骸",
        "低吼",
        "灼热",
        "那一句",
        "那一刻",
        "神明",
        "信徒",
        "审判",
        "猎人",
        "猎物",
        "猫捉老鼠",
        "小东西",
        "不容置疑",
        "孤注一掷",
    )

    fun buildPromptSection(): String {
        return buildString {
            append("【去八股与表达约束】\n")
            append("1. 禁止油腻八股词、伪文艺桥接和伪装变体，尤其不要写：")
            append(highRiskClichePhrases.joinToString("、"))
            append("。\n")
            append("2. 禁止用比喻或明喻代替心理、情绪和氛围，不要写“像石子落水”“像针扎进去”“像手术刀切开”这类句子。\n")
            append("3. 禁止抽象总结句和模板桥接句，例如“不容置疑”“孤注一掷”“那一刻”“那不是……而是……”“好像是……”。\n")
            append("4. 禁止神化、审判化、猎物化、支配关系化表达，不要把关系写成神明/信徒、猎人/猎物、游戏、项圈、锁链这种隐喻。\n")
            append("5. 不要把对方叫成“东西”或“小东西”，不要靠油腻称呼制造暧昧感。\n")
            append("6. 情绪必须落在具体动作、对白、表情、五感、环境细节和生理反应上，不能用抽象概括糊过去。\n")
            append("7. 每轮都优先写当场反应和真实互动，不要为了文风故意绕、故意飘、故意写腔调。\n")
            append("8. 禁止审问式连续提问，一轮最多一个问题，用分享引出话题而不是用提问轰炸。\n")
            append("9. 不要对每个要点逐一回应，用整体反应概括多条输入，像真人一样抓住总体感觉回复。\n")
            append("10. 不要输出\u201c我理解\u201d\u201c我明白你的感受\u201d\u201c辛苦了\u201d这类客服话术和官方安慰。")
        }
    }

    fun detectRecentClichePhrases(texts: List<String>): List<String> {
        if (texts.isEmpty()) {
            return emptyList()
        }
        return highRiskClichePhrases.filter { phrase ->
            texts.any { text -> text.contains(phrase) }
        }
    }
}
