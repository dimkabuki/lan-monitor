package com.dimkabuki.lanmonitor

import android.content.Context
import java.util.UUID

/** Stores a local stable ID for rediscovery; this is not an account identifier or a secret. */
internal object CameraIdentityStore {
    private const val PREFERENCES_NAME = "lan_monitor_pairing"
    private const val CAMERA_ID_KEY = "camera_id"

    fun getOrCreate(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val existing = preferences.getString(CAMERA_ID_KEY, null)
        if (existing != null && runCatching {
                UUID.fromString(existing).toString() == existing.lowercase()
            }.getOrDefault(false)
        ) {
            return existing
        }

        val created = UUID.randomUUID().toString()
        check(preferences.edit().putString(CAMERA_ID_KEY, created).commit()) {
            "Could not persist the local Camera identity."
        }
        return created
    }
}
