package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagePartTest {
    @Test
    fun toContentMirror_returnsInviteFallback() {
        val mirror = listOf(
            inviteMessagePart(
                target = "陆宴清",
                place = "江边步道",
                time = "今晚九点",
            ),
        ).toContentMirror()

        assertEquals("邀约：江边步道", mirror)
    }

    @Test
    fun toContentMirror_returnsPunishFallback() {
        val mirror = listOf(
            punishMessagePart(
                method = "戒尺",
                count = "三下",
                intensity = PunishIntensity.HEAVY,
                reason = "撒谎",
            ),
        ).toContentMirror()

        assertEquals("惩罚：戒尺 · 三下", mirror)
    }

    @Test
    fun normalizeChatMessageParts_trimsTaskMetadata() {
        val normalized = normalizeChatMessageParts(
            listOf(
                taskMessagePart(
                    title = " 寻找钥匙 ",
                    objective = " 在旧图书馆找到铜钥匙 ",
                    reward = " 一个答案 ",
                    deadline = " 天亮前 ",
                ),
            ),
        ).single()

        assertEquals("寻找钥匙", normalized.specialMetadataValue("title"))
        assertEquals("在旧图书馆找到铜钥匙", normalized.specialMetadataValue("objective"))
        assertEquals("一个答案", normalized.specialMetadataValue("reward"))
        assertEquals("天亮前", normalized.specialMetadataValue("deadline"))
    }

    @Test
    fun toSpecialPlayCopyText_includesGiftPayload() {
        val copyText = giftMessagePart(
            target = "陆宴清",
            item = "黑胶唱片",
            note = "别再熬夜了",
        ).toSpecialPlayCopyText()

        assertTrue(copyText.contains("送礼对象：陆宴清"))
        assertTrue(copyText.contains("礼物：黑胶唱片"))
        assertTrue(copyText.contains("附言：别再熬夜了"))
    }

    @Test
    fun toSpecialPlayCopyText_includesPunishPayload() {
        val copyText = punishMessagePart(
            method = "鞭子",
            count = "三下",
            intensity = PunishIntensity.MEDIUM,
            reason = "撒谎",
            note = "边抽边认错",
        ).toSpecialPlayCopyText()

        assertTrue(copyText.contains("方式：鞭子"))
        assertTrue(copyText.contains("次数：三下"))
        assertTrue(copyText.contains("强度：中"))
        assertTrue(copyText.contains("原因：撒谎"))
        assertTrue(copyText.contains("附注：边抽边认错"))
    }

    @Test
    fun giftImageStateHelpers_updateGiftMetadata() {
        val generating = giftMessagePart(
            target = "陆宴清",
            item = "黑胶唱片",
        ).withGiftImageGenerating()

        assertEquals(GiftImageStatus.GENERATING, generating.giftImageStatus())

        val succeeded = generating.withGiftImageSuccess(
            imageUri = "/tmp/gift.png",
            mimeType = "image/png",
            fileName = "gift.png",
        )

        assertEquals(GiftImageStatus.SUCCEEDED, succeeded.giftImageStatus())
        assertEquals("/tmp/gift.png", succeeded.giftImageUri())
        assertEquals("image/png", succeeded.giftImageMimeType())
        assertEquals("gift.png", succeeded.giftImageFileName())
        assertTrue(succeeded.hasGiftGeneratedImage())

        val failed = succeeded.withGiftImageFailure("礼物图生成失败")
        assertEquals(GiftImageStatus.FAILED, failed.giftImageStatus())
        assertEquals("礼物图生成失败", failed.giftImageErrorMessage())
        assertFalse(failed.hasGiftGeneratedImage())
    }
}
