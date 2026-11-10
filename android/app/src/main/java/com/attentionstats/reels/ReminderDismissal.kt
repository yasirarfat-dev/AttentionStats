package com.attentionstats.reels

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

/** A user's dismissal must wait for queue capacity, rather than use lossy observation admission. */
internal object ReminderDismissal {
    fun enqueue(scope: CoroutineScope, queue: SendChannel<suspend () -> Unit>,
        reset: () -> Unit, acknowledge: () -> Unit) = scope.launch {
        queue.send {
            reset()
            acknowledge()
        }
    }
}
