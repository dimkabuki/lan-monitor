package com.dimkabuki.lanmonitor

/**
 * Same-process stop/error bridge between the ongoing notification and the UI-owned session.
 */
internal object ActiveSessionRegistry {
    private data class Registration(
        val owner: Any,
        val onStop: () -> Unit,
        val onFailure: (String) -> Unit,
    )

    @Volatile
    private var registration: Registration? = null

    fun register(owner: Any, onStop: () -> Unit, onFailure: (String) -> Unit) {
        synchronized(this) {
            registration = Registration(owner, onStop, onFailure)
        }
    }

    fun unregister(owner: Any) {
        synchronized(this) {
            if (registration?.owner === owner) registration = null
        }
    }

    fun requestStop() {
        val callback = registration?.onStop ?: return
        runCatching { callback() }
    }

    fun reportFailure(message: String) {
        val active = registration ?: return
        runCatching { active.onFailure(message) }
    }
}
