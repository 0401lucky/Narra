package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.CompanionRequestGuard
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/** 与三协议共用的最后发送点绑定，图片读取、兼容回退期间来源变化也会阻止发送。 */
internal class GatewayCompanionSession(private val guard: CompanionRequestGuard) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GatewayCompanionSession>

    suspend fun validate() {
        if (!guard.validate()) {
            currentCoroutineContext()[GatewayRecallSession]?.rejectedBeforeSend()
            error("本轮授权原文或身份已改变，请重新发起")
        }
    }
}
