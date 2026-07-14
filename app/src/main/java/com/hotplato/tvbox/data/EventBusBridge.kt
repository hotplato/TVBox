package com.hotplato.tvbox.data

import com.hotplato.tvbox.event.RefreshEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Bridges legacy EventBus events into Kotlin Flow for Compose ViewModels.
 * Main Compose UI must not register EventBus directly.
 */
object EventBusBridge {
    fun refreshEvents(): Flow<RefreshEvent> = callbackFlow {
        val subscriber = object {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onEvent(event: RefreshEvent) {
                trySend(event)
            }
        }
        EventBus.getDefault().register(subscriber)
        awaitClose { EventBus.getDefault().unregister(subscriber) }
    }
}
