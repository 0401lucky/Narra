package com.example.myapplication.compliance

/**
 * 应用级使用须知与年龄声明。
 *
 * 条款内容由首次确认页和设置页共同消费；修改具有实质影响时必须同步提升版本号。
 */
data class CompliancePolicySection(
    val title: String,
    val body: String,
)

object CompliancePolicy {
    const val CURRENT_VERSION = "2026-07-15-v1"
    const val TITLE = "Narra 使用须知与年龄声明"

    val sections: List<CompliancePolicySection> = listOf(
        CompliancePolicySection(
            title = "1. 使用条件",
            body = "Narra 仅向年满 18 周岁的用户开放。本人需要在进入应用前确认已满 18 周岁。"
                + "本确认是应用的使用条件，不等同于实名年龄核验；应用不会因本声明收集身份证号等高敏感身份信息。",
        ),
        CompliancePolicySection(
            title = "2. AI 不是真人",
            body = "Narra 中的角色、助手、电话、邮箱、动态和其他互动内容由人工智能模型生成，"
                + "不代表真实人物、真实关系或真实机构。请不要把模拟回应理解为对方的真实意愿、承诺或事实证明。",
        ),
        CompliancePolicySection(
            title = "3. 内容可能不准确",
            body = "AI 可能产生错误、过时、遗漏、虚构或不完整的内容。涉及事实、学习、工作、消费、"
                + "出行或其他重要决定时，请通过可靠来源独立核实，不要仅凭 AI 输出作出决定。",
        ),
        CompliancePolicySection(
            title = "4. 不替代专业意见",
            body = "Narra 不提供医疗、心理治疗、法律、金融、保险或紧急救援服务，相关内容不能替代"
                + "专业人员的判断。遇到人身安全、急性疾病、自伤或他伤风险时，请立即联系当地急救、"
                + "警方、专业机构或可信任的现实联系人。",
        ),
        CompliancePolicySection(
            title = "5. 角色互动与身心边界",
            body = "角色扮演和拟人化互动属于虚构体验，不构成现实亲密关系。请控制使用时间，保持与"
                + "现实生活的联系，不要把 AI 当作唯一的情感支持来源。如果出现明显依赖、困扰、失眠、"
                + "情绪恶化或无法停止使用等情况，请暂停使用并寻求现实中的帮助。",
        ),
        CompliancePolicySection(
            title = "6. 内容与行为边界",
            body = "请遵守法律法规和平台规则，不利用应用制作、传播或诱导违法、有害、暴力、诈骗、"
                + "侵犯他人权益或涉及未成年人不当内容。用户对自己输入、导入、生成、保存和分享的内容"
                + "及其后果承担相应责任；AI 生成内容也需要由用户自行判断和处理。",
        ),
        CompliancePolicySection(
            title = "7. 隐私与敏感信息",
            body = "请勿向角色卡、提示词、聊天内容、图片、文件或其他输入中填写身份证号、银行卡号、"
                + "密码、API Key、精确住址、未公开联系方式或其他不必要的敏感信息。根据你的配置，"
                + "内容可能会发送到你选择的第三方 AI 服务商；请同时了解相关服务商的隐私规则。",
        ),
        CompliancePolicySection(
            title = "8. 服务可用性",
            body = "应用依赖设备、网络、模型服务和用户配置，可能出现延迟、限流、错误、内容不可用、"
                + "数据丢失或服务中断。Narra 不保证任何模型、功能或网络服务持续可用。请对重要内容"
                + "保留必要的独立备份。",
        ),
        CompliancePolicySection(
            title = "9. 条款更新",
            body = "本须知用于说明 AI 互动的主要风险和使用条件，不构成法律意见，也不免除应用依法应"
                + "承担的责任。重要内容发生变化时，应用会提升条款版本并要求重新阅读确认；你不同意"
                + "更新内容时，可以停止使用应用。",
        ),
    )
}
