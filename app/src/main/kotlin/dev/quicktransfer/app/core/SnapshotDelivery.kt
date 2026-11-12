// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.core

/** Capture and enqueue under the writer's lock so an old publisher cannot overtake a terminal update. */
internal fun publishSnapshot(lock: Any, read: () -> String, deliver: (String) -> Unit) {
    synchronized(lock) { deliver(read()) }
}
