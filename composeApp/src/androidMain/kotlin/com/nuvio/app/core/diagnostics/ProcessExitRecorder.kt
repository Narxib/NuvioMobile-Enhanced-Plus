package com.nuvio.app.core.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.logging.InAppLogger
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Surfaces why the previous process died.
 *
 * A native crash (a SIGSEGV inside libstagefright or a vendor codec) never reaches a JVM
 * uncaught-exception handler: the process is gone before Kotlin runs anything, so Debug Logs is
 * empty after one of those and the app just "closes". The system keeps the exit record and the
 * tombstone instead, and an app may read its own history through [ActivityManager], which works
 * offline and survives reboots. Copy that into a file and into the in-app log on the next launch
 * so a crash is diagnosable without a cable.
 *
 * Only the tombstone is persisted, never the in-app log buffer, so playback URLs and headers are
 * not written to disk.
 */
internal object ProcessExitRecorder {

    private const val preferencesName = "nuvio_process_exit"
    private const val lastSeenTimestampKey = "last_seen_exit_timestamp"
    private const val lastSeenUncaughtKey = "last_seen_uncaught_timestamp"
    private const val historyLimit = 12
    private const val traceByteLimit = 128 * 1024
    private const val loggedTraceLines = 24
    private const val retainedTraceFiles = 8
    private const val tag = "App/ProcessExit"
    private const val logcatTag = "NuvioProcessExit"

    fun recordPendingExits(context: Context) {
        val applicationContext = context.applicationContext
        Thread(
            { runCatching { record(applicationContext) } },
            "process-exit-recorder",
        ).start()
    }

    /**
     * Native crashes are covered by the system tombstone, but a Kotlin/Java exception leaves the
     * exit record with no trace at all, so grab it before the process dies.
     */
    fun installCrashHandler(context: Context) {
        val applicationContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeUncaughtCrash(applicationContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeUncaughtCrash(context: Context, thread: Thread, error: Throwable) {
        val timestamp = System.currentTimeMillis()
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString()
        val text = buildString {
            appendLine("uncaught exception at ${formatTimestamp(timestamp)}")
            appendLine("thread=${thread.name} pid=${Process.myPid()}")
            appendLine("version=${AppVersionConfig.VERSION_NAME} (${AppVersionConfig.VERSION_CODE})")
            appendLine()
            append(stack)
        }
        val file = File(crashDirectory(context), "${fileNamePrefix(timestamp)}-uncaught.txt")
        file.writeText(text)
        Log.e(logcatTag, "uncaught ${error::class.java.simpleName} in ${thread.name}: ${error.message}")
        Log.e(logcatTag, "uncaught stack saved to ${file.absolutePath}")
    }

    private fun record(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            recordExits(context)
        }
        reportSavedUncaught(context)
    }

    private fun recordExits(context: Context) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val lastSeen = preferences.getLong(lastSeenTimestampKey, 0L)
        val exits = activityManager
            .getHistoricalProcessExitReasons(context.packageName, 0, historyLimit)
            .orEmpty()
            .filter { it.timestamp > lastSeen }
            .sortedBy { it.timestamp }
        if (exits.isEmpty()) return

        var newest = lastSeen
        for (exit in exits) {
            newest = maxOf(newest, exit.timestamp)
            report(context, exit)
        }
        preferences.edit().putLong(lastSeenTimestampKey, newest).apply()
    }

    /** Reports Java/Kotlin stacks captured by [installCrashHandler] on a previous run. */
    private fun reportSavedUncaught(context: Context) {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val lastSeen = preferences.getLong(lastSeenUncaughtKey, 0L)
        val crashes = crashDirectory(context)
            .listFiles { file -> file.name.endsWith("-uncaught.txt") }
            .orEmpty()
            .filter { it.lastModified() > lastSeen }
            .sortedBy { it.lastModified() }
        if (crashes.isEmpty()) return

        var newest = lastSeen
        for (file in crashes) {
            newest = maxOf(newest, file.lastModified())
            InAppLogger.error(tag, "previous run crashed with an uncaught exception")
            Log.e(logcatTag, "previous run crashed with an uncaught exception")
            runCatching {
                file.readLines().take(loggedTraceLines + 4).forEach { line ->
                    InAppLogger.error(tag, line)
                    Log.e(logcatTag, line)
                }
            }
        }
        preferences.edit().putLong(lastSeenUncaughtKey, newest).apply()
    }

    private fun report(context: Context, exit: ApplicationExitInfo) {
        val reason = reasonName(exit.reason)
        val header = "previous process ended: reason=$reason status=${exit.status} " +
            "importance=${exit.importance} pssKb=${exit.pss} rssKb=${exit.rss} " +
            "at=${formatTimestamp(exit.timestamp)} description=${exit.description.orEmpty()}"
        val worthInvestigating = exit.reason in investigatedReasons
        if (worthInvestigating) {
            InAppLogger.warn(tag, header)
            Log.w(logcatTag, header)
        } else {
            InAppLogger.debug(tag, header)
            Log.i(logcatTag, header)
            return
        }

        val trace = readTrace(exit) ?: run {
            InAppLogger.info(tag, "no tombstone recorded for reason=$reason")
            Log.i(logcatTag, "no tombstone recorded for reason=$reason")
            return
        }
        val saved = saveTrace(context, exit, reason, trace)
        if (saved != null) {
            InAppLogger.info(tag, "tombstone saved to ${saved.absolutePath}")
            Log.i(logcatTag, "tombstone saved to ${saved.absolutePath}")
        }

        summarize(trace).forEach { line ->
            InAppLogger.error(tag, line)
            Log.e(logcatTag, line)
        }
    }

    private fun readTrace(exit: ApplicationExitInfo): String? = runCatching {
        exit.traceInputStream?.use { stream ->
            val buffer = ByteArray(traceByteLimit)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            if (total == 0) null else String(buffer, 0, total, Charsets.UTF_8)
        }
    }.getOrNull()

    private fun saveTrace(
        context: Context,
        exit: ApplicationExitInfo,
        reason: String,
        trace: String,
    ): File? = runCatching {
        val directory = crashDirectory(context)
        val file = File(directory, "${fileNamePrefix(exit.timestamp)}-$reason.txt")
        file.writeText(trace)
        directory.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(retainedTraceFiles)
            ?.forEach { it.delete() }
        file
    }.getOrNull()

    private fun crashDirectory(context: Context): File =
        File(context.filesDir, "crash-logs").apply { mkdirs() }

    private fun fileNamePrefix(timestamp: Long): String =
        formatTimestamp(timestamp).replace(' ', '-').replace(':', '-')

    /**
     * Keeps the useful part of a tombstone (signal, abort message and the first frames) so the
     * crash is readable inside Debug Logs without scrolling the whole dump.
     */
    private fun summarize(trace: String): List<String> {
        val lines = trace.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        val summary = mutableListOf<String>()
        listOf("signal ", "Cause:", "Abort message", "Cmdline", "pid: ", "Build fingerprint")
            .forEach { prefix ->
                lines.firstOrNull { it.trimStart().startsWith(prefix) }?.let(summary::add)
            }
        val backtraceIndex = lines.indexOfFirst { it.trim() == "backtrace:" }
        if (backtraceIndex >= 0) {
            summary += lines.subList(
                backtraceIndex,
                minOf(lines.size, backtraceIndex + loggedTraceLines),
            )
        }
        return summary.distinct()
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native-crash"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization-failure"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low-memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive-resource-usage"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user-requested"
        ApplicationExitInfo.REASON_OTHER -> "other"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit-self"
        else -> "reason-$reason"
    }

    /** Crashes, ANRs and system kills are worth a tombstone; installs and user kills are not. */
    private val investigatedReasons = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    )

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
}
