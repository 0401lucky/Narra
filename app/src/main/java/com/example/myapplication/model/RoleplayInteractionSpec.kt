package com.example.myapplication.model

/**
 * 剧情场景里三项紧密联动的交互开关集合：
 *   - [interactionMode]        交互模式（线下长文 / 线下对白 / 线上电话）
 *   - [longformModeEnabled]    长文小说模式开关
 *   - [enableRoleplayProtocol] RP 协议输出开关
 *
 * 字段间存在跨字段联动（例如启用长文会强制 RP 协议关闭、切换到线上电话会强制启用 RP 协议等）。
 * 把规则集中到这里，UI 层只负责按字段覆写结果。
 */
data class RoleplayInteractionSpec(
    val interactionMode: RoleplayInteractionMode,
    val longformModeEnabled: Boolean,
    val enableRoleplayProtocol: Boolean,
) {
    /**
     * 规范化历史数据：旧版可能出现 [longformModeEnabled] = true 但 [interactionMode] 仍停在
     * [RoleplayInteractionMode.OFFLINE_DIALOGUE] 的错位，这里统一提升为 OFFLINE_LONGFORM。
     */
    fun normalized(): RoleplayInteractionSpec {
        return if (
            longformModeEnabled &&
            interactionMode == RoleplayInteractionMode.OFFLINE_DIALOGUE
        ) {
            copy(interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM)
        } else {
            this
        }
    }

    /**
     * 切换交互模式：
     *   - OFFLINE_LONGFORM：强制开启长文，关闭 RP 协议（长文与 RP 协议互斥）
     *   - OFFLINE_DIALOGUE：强制关闭长文；不动 RP 协议
     *   - ONLINE_PHONE：强制关闭长文，强制启用 RP 协议（线上走 JSON 数组协议）
     */
    fun withInteractionMode(mode: RoleplayInteractionMode): RoleplayInteractionSpec = when (mode) {
        RoleplayInteractionMode.OFFLINE_LONGFORM -> copy(
            interactionMode = mode,
            longformModeEnabled = true,
            enableRoleplayProtocol = false,
        )
        RoleplayInteractionMode.OFFLINE_DIALOGUE -> copy(
            interactionMode = mode,
            longformModeEnabled = false,
        )
        RoleplayInteractionMode.ONLINE_PHONE -> copy(
            interactionMode = mode,
            longformModeEnabled = false,
            enableRoleplayProtocol = true,
        )
    }

    /**
     * 切换长文开关：
     *   - 开启：交互模式提升为 OFFLINE_LONGFORM，关闭 RP 协议
     *   - 关闭：若当前停在 OFFLINE_LONGFORM 则回落到 OFFLINE_DIALOGUE；否则保持当前模式
     */
    fun withLongform(enabled: Boolean): RoleplayInteractionSpec {
        return if (enabled) {
            copy(
                longformModeEnabled = true,
                interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
                enableRoleplayProtocol = false,
            )
        } else {
            val nextMode = if (interactionMode == RoleplayInteractionMode.OFFLINE_LONGFORM) {
                RoleplayInteractionMode.OFFLINE_DIALOGUE
            } else {
                interactionMode
            }
            copy(longformModeEnabled = false, interactionMode = nextMode)
        }
    }

    /**
     * 切换 RP 协议：
     *   - 仅在"非长文 && 非线上电话"时，把当前模式规范化到 OFFLINE_DIALOGUE，避免与长文残余状态冲突。
     *   - 其它情况下只翻转开关值。
     */
    fun withRoleplayProtocol(enabled: Boolean): RoleplayInteractionSpec {
        val nextMode = if (
            !longformModeEnabled &&
            interactionMode != RoleplayInteractionMode.ONLINE_PHONE
        ) {
            RoleplayInteractionMode.OFFLINE_DIALOGUE
        } else {
            interactionMode
        }
        return copy(enableRoleplayProtocol = enabled, interactionMode = nextMode)
    }
}
