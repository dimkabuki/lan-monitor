package com.dimkabuki.lanmonitor

import android.content.Context

/**
 * Process-level controller owner shared by the UI and its foreground service.
 *
 * Compose screen disposal releases only a UI attachment. A controller with active work stays
 * strongly reachable while the Activity is recreated or the user leaves its role screen.
 */
internal object SessionControllerRetentionPolicy {
    fun shouldDispose(
        uiAttachmentCount: Int,
        serviceAttachmentCount: Int,
        hasSessionWork: Boolean,
    ): Boolean = uiAttachmentCount <= 0 && serviceAttachmentCount <= 0 && !hasSessionWork
}

internal object SessionControllerStore {
    private data class Entry(
        val session: LanWebRtcSession,
        var uiAttachments: Int = 0,
        var serviceAttachments: Int = 0,
    )

    private val lock = Any()
    private val entries = mutableMapOf<AppRole, Entry>()

    fun attachUi(context: Context, role: AppRole): LanWebRtcSession = synchronized(lock) {
        var entry = entries[role]
        if (entry == null || entry.session.isDisposed) {
            entry = Entry(LanWebRtcSession(context.applicationContext, role))
            entries[role] = entry
        }
        entry.uiAttachments += 1
        entry.session
    }

    fun existing(role: AppRole): LanWebRtcSession? = synchronized(lock) {
        entries[role]?.session?.takeUnless { it.isDisposed }
    }

    fun stopIfActive(role: AppRole) {
        existing(role)?.takeIf { it.hasSessionWork }?.stopSession()
    }

    fun admissionError(session: LanWebRtcSession): String? = synchronized(lock) {
        val other = entries.values.firstOrNull {
            it.session !== session && it.session.hasSessionWork
        }
        other?.let {
            "Another ${it.session.role.label} session is already active. Stop it before starting ${session.role.label}."
        }
    }

    fun findForService(role: AppRole): LanWebRtcSession? = synchronized(lock) {
        val entry = entries[role] ?: return@synchronized null
        if (!entry.session.hasSessionWork) return@synchronized null
        if (entries.values.any { it.session !== entry.session && it.session.hasSessionWork }) {
            return@synchronized null
        }
        entry.session
    }

    fun retainService(session: LanWebRtcSession): Boolean = synchronized(lock) {
        val entry = entries[session.role] ?: return@synchronized false
        if (entry.session !== session || !session.hasSessionWork) return@synchronized false
        if (entries.values.any { it.session !== session && it.session.hasSessionWork }) {
            return@synchronized false
        }
        if (entry.serviceAttachments == 0) entry.serviceAttachments = 1
        true
    }

    fun releaseService(session: LanWebRtcSession) {
        val dispose = synchronized(lock) {
            val entry = entries[session.role]
            if (entry == null || entry.session !== session) {
                false
            } else {
                entry.serviceAttachments = (entry.serviceAttachments - 1).coerceAtLeast(0)
                val shouldDispose = SessionControllerRetentionPolicy.shouldDispose(
                    entry.uiAttachments,
                    entry.serviceAttachments,
                    session.hasSessionWork,
                )
                if (shouldDispose) entries.remove(session.role)
                shouldDispose
            }
        }
        if (dispose) session.disposeAfterCleanup()
    }

    fun detachUi(session: LanWebRtcSession) {
        val dispose = synchronized(lock) {
            val entry = entries[session.role]
            if (entry == null || entry.session !== session) {
                false
            } else {
                entry.uiAttachments = (entry.uiAttachments - 1).coerceAtLeast(0)
                val shouldDispose = SessionControllerRetentionPolicy.shouldDispose(
                    entry.uiAttachments,
                    entry.serviceAttachments,
                    session.hasSessionWork,
                )
                if (shouldDispose) entries.remove(session.role)
                shouldDispose
            }
        }
        if (dispose) session.dispose()
    }

    fun onSessionEnded(session: LanWebRtcSession) {
        val dispose = synchronized(lock) {
            val entry = entries[session.role]
            if (entry == null || entry.session !== session) {
                false
            } else {
                val shouldDispose = SessionControllerRetentionPolicy.shouldDispose(
                    entry.uiAttachments,
                    entry.serviceAttachments,
                    session.hasSessionWork,
                )
                if (shouldDispose) entries.remove(session.role)
                shouldDispose
            }
        }
        if (dispose) session.disposeAfterCleanup()
    }
}
