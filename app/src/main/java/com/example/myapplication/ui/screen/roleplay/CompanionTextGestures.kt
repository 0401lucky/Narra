package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import kotlinx.coroutines.CancellationException

/** 长按在松手时交出菜单；同一次按压的拖动/滚动/取消均不交出点击或选区。 */
internal fun Modifier.companionTextGestures(
    identity: Any?, onClick: (() -> Unit)?, onLongClick: () -> Unit,
    onCancel: () -> Unit
): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val cancel by rememberUpdatedState(onCancel)
    val currentIdentity by rememberUpdatedState(identity)
    semantics {
        if (click != null) this.onClick { click?.invoke(); true }
        this.onLongClick(label = "选择片段") { longClick(); true }
    }.pointerInput(identity) {
        val owner = identity
        awaitEachGesture {
            try {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress == null) {
                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                    if (up != null && !up.pressed && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                        if (!up.isConsumed) {
                            up.consume()
                            if (owner == currentIdentity) click?.invoke()
                        }
                    } else if (owner == currentIdentity) cancel()
                } else {
                    var canceled = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            canceled = true
                            break
                        }
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                    }
                    if (owner == currentIdentity) {
                        if (canceled) cancel() else longClick()
                    }
                }
            } catch (cancelled: CancellationException) {
                if (owner == currentIdentity) cancel()
                throw cancelled
            }
        }
    }
}
