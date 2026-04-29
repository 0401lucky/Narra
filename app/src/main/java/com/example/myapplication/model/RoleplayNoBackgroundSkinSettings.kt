package com.example.myapplication.model

data class RoleplayNoBackgroundSkinSettings(
    val preset: RoleplayNoBackgroundSkinPreset = RoleplayNoBackgroundSkinPreset.NARRA,
    val maxWidthPercent: Int = ROLEPLAY_SKIN_DEFAULT_MAX_WIDTH_PERCENT,
    val bubbleRadiusDp: Int = ROLEPLAY_SKIN_DEFAULT_RADIUS_DP,
    val bubblePaddingHorizontalDp: Int = ROLEPLAY_SKIN_DEFAULT_PADDING_HORIZONTAL_DP,
    val bubblePaddingVerticalDp: Int = ROLEPLAY_SKIN_DEFAULT_PADDING_VERTICAL_DP,
    val showBubbleTail: Boolean = false,
) {
    fun normalized(): RoleplayNoBackgroundSkinSettings {
        return copy(
            maxWidthPercent = maxWidthPercent.coerceIn(
                ROLEPLAY_SKIN_MIN_MAX_WIDTH_PERCENT,
                ROLEPLAY_SKIN_MAX_MAX_WIDTH_PERCENT,
            ),
            bubbleRadiusDp = bubbleRadiusDp.coerceIn(
                ROLEPLAY_SKIN_MIN_RADIUS_DP,
                ROLEPLAY_SKIN_MAX_RADIUS_DP,
            ),
            bubblePaddingHorizontalDp = bubblePaddingHorizontalDp.coerceIn(
                ROLEPLAY_SKIN_MIN_PADDING_HORIZONTAL_DP,
                ROLEPLAY_SKIN_MAX_PADDING_HORIZONTAL_DP,
            ),
            bubblePaddingVerticalDp = bubblePaddingVerticalDp.coerceIn(
                ROLEPLAY_SKIN_MIN_PADDING_VERTICAL_DP,
                ROLEPLAY_SKIN_MAX_PADDING_VERTICAL_DP,
            ),
        )
    }
}

enum class RoleplayNoBackgroundSkinPreset(
    val storageValue: String,
    val displayName: String,
) {
    NARRA("narra", "Narra"),
    WECHAT("wechat", "微信"),
    QQ("qq", "QQ"),
    TELEGRAM("telegram", "Telegram"),
    KAKAO("kakao", "KakaoTalk"),
    RETRO("retro", "Retro"),
    POLKADOT("polkadot", "波点复古"),
    PIXEL("pixel", "复古像素"),
    ROSE("rose", "美丽少女"),
    NOIR("noir", "哥特夜诗"),
    Y2K("y2k", "Y2K"),
    IMESSAGE("imessage", "iMessage"),
    LIQUID_GLASS("liquidglass", "液态玻璃");

    companion object {
        fun fromStorageValue(value: String): RoleplayNoBackgroundSkinPreset {
            val normalized = value.trim()
            return entries.firstOrNull {
                it.storageValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: NARRA
        }
    }
}

const val ROLEPLAY_SKIN_MIN_MAX_WIDTH_PERCENT = 64
const val ROLEPLAY_SKIN_MAX_MAX_WIDTH_PERCENT = 92
const val ROLEPLAY_SKIN_DEFAULT_MAX_WIDTH_PERCENT = 78

const val ROLEPLAY_SKIN_MIN_RADIUS_DP = 4
const val ROLEPLAY_SKIN_MAX_RADIUS_DP = 28
const val ROLEPLAY_SKIN_DEFAULT_RADIUS_DP = 18

const val ROLEPLAY_SKIN_MIN_PADDING_HORIZONTAL_DP = 8
const val ROLEPLAY_SKIN_MAX_PADDING_HORIZONTAL_DP = 20
const val ROLEPLAY_SKIN_DEFAULT_PADDING_HORIZONTAL_DP = 14

const val ROLEPLAY_SKIN_MIN_PADDING_VERTICAL_DP = 6
const val ROLEPLAY_SKIN_MAX_PADDING_VERTICAL_DP = 18
const val ROLEPLAY_SKIN_DEFAULT_PADDING_VERTICAL_DP = 10
