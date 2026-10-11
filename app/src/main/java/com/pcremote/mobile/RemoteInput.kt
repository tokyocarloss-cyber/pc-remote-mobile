package com.pcremote.mobile

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Serial gamepad transitions survive disposal of the composable that created them. */
object RemoteInput {
    private data class Command(val host: String, val body: String)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Command>(64)
    init {
        scope.launch {
            for (command in queue) {
                if (command.host == Api.host && Api.connected) Api.post("/gamepad",command.body)
            }
        }
    }
    fun pad(body: String) {
        if (queue.trySend(Command(Api.host,body)).isFailure) {
            while (queue.tryReceive().isSuccess) { }
            queue.trySend(Command(Api.host,"{\"kind\":\"reset\"}"))
        }
    }
    fun reset() = pad("{\"kind\":\"reset\"}")
}
