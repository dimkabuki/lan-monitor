package com.dimkabuki.lanmonitor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit opt-in application diagnostics. Each line is appended and the stream is closed
 * immediately so the file remains usable if Android kills the process mid-session.
 *
 * The logger never receives pairing tokens, QR payloads, SDP, or raw ICE candidates.
 */
internal object DiagnosticLog {
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    private const val DOWNLOADS_SUBDIRECTORY = "Lan Monitor"

    private val lock = Any()
    val enabled = mutableStateOf(false)
    val statusMessage = mutableStateOf("Diagnostic logging is off. Enable only while reproducing an issue.")

    private var resolver: android.content.ContentResolver? = null
    private var fileUri: Uri? = null
    private var fileName: String? = null
    private var bytesWritten = 0L

    fun setEnabled(context: Context, role: AppRole, requested: Boolean) {
        synchronized(lock) {
            if (requested == enabled.value) return

            if (!requested) {
                appendLineLocked(role, "diagnostic_logging_disabled", "")
                if (enabled.value) {
                    enabled.value = false
                    statusMessage.value = fileName?.let {
                        "Saved to Download/$DOWNLOADS_SUBDIRECTORY/$it"
                    } ?: "Diagnostic logging is off."
                }
                resolver = null
                fileUri = null
                return
            }

            val contentResolver = context.applicationContext.contentResolver
            val name = buildFileName(role)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_SUBDIRECTORY",
                )
            }
            val uri = runCatching {
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }.getOrNull()

            if (uri == null) {
                enabled.value = false
                statusMessage.value = "Could not create a diagnostic file in Downloads. Check available storage and try again."
                return
            }

            resolver = contentResolver
            fileUri = uri
            fileName = name
            bytesWritten = 0L
            enabled.value = true
            statusMessage.value = "Writing to Download/$DOWNLOADS_SUBDIRECTORY/$name"
            appendLineLocked(
                role,
                "diagnostic_logging_enabled",
                "schema=1; max_file_bytes=$MAX_FILE_BYTES; sensitive_payloads=excluded",
            )
        }
    }

    fun log(role: AppRole, event: String, details: String = "") {
        synchronized(lock) {
            appendLineLocked(role, event, details)
        }
    }

    private fun appendLineLocked(role: AppRole, event: String, details: String) {
        if (!enabled.value) return
        val targetResolver = resolver ?: return
        val targetUri = fileUri ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        val safeEvent = event.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
        val safeDetails = DiagnosticLogSanitizer.sanitize(details)
        val line = "$timestamp role=${role.name} event=$safeEvent" +
            if (safeDetails.isBlank()) "\n" else " details=$safeDetails\n"
        val bytes = line.toByteArray(StandardCharsets.UTF_8)

        if (bytesWritten + bytes.size > MAX_FILE_BYTES) {
            enabled.value = false
            statusMessage.value = "Diagnostic log reached the 2 MiB safety limit. Saved to Download/$DOWNLOADS_SUBDIRECTORY/$fileName"
            return
        }

        try {
            val output = targetResolver.openOutputStream(targetUri, "wa")
                ?: throw IllegalStateException("Downloads provider did not open the log file")
            output.use {
                it.write(bytes)
                it.flush()
            }
            bytesWritten += bytes.size
        } catch (error: Exception) {
            enabled.value = false
            statusMessage.value =
                "Diagnostic logging stopped because the file could not be written. Any earlier log remains in Download/$DOWNLOADS_SUBDIRECTORY."
            resolver = null
            fileUri = null
        }
    }

    private fun buildFileName(role: AppRole): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "lan-monitor-${role.name.lowercase(Locale.US)}-$timestamp.txt"
    }
}

/** Redacts sensitive assignments and prevents a multi-line payload from entering a log record. */
internal object DiagnosticLogSanitizer {
    private val sensitiveAssignment = Regex(
        "(?i)(pairing[ _-]?token|token|secret|authorization|password|sdp|candidate)\\s*[:=]\\s*[^\\s,;]+",
    )
    private val lineBreaks = Regex("[\\r\\n\\t]+")

    fun sanitize(value: String): String {
        if (value.contains("a=candidate:", ignoreCase = true) ||
            value.contains("sdp=", ignoreCase = true) ||
            value.contains("m=audio ", ignoreCase = true) ||
            value.contains("m=video ", ignoreCase = true)
        ) {
            return "[REDACTED SDP/ICE payload]"
        }

        return value
            .replace(lineBreaks, " ")
            .replace(sensitiveAssignment) { match ->
                "${match.groupValues[1]}=[REDACTED]"
            }
            .take(700)
    }
}
