// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** The native response budget includes dispatch, processing and asynchronous delivery. */
internal fun awaitNativeReply(reply: CompletableFuture<Any>, timeoutMillis: Long, executor: ExecutorService, dispatch: () -> Unit): Any {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    val work = executor.submit {
        try { dispatch() } catch (error: Exception) { reply.completeExceptionally(error) }
    }
    var received = false
    try {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw TimeoutException("Source service response timed out")
        return reply.get(remaining, TimeUnit.NANOSECONDS).also { received = true }
    } catch (error: ExecutionException) {
        throw error.cause ?: error
    } finally { if (!received) work.cancel(true) }
}
