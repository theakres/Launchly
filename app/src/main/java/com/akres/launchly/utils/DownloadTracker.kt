package com.akres.launchly.utils

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import com.akres.launchly.services.DownloadNotifications
import com.akres.launchly.services.DownloadService
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

data class DownloadInfo(
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val speed: Float = 0f, // bytes per second
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val etaSeconds: Long get() = if (speed > 1f) ((total - downloaded) / speed).toLong().coerceAtLeast(0) else -1L
}

/**
 * Process-wide holder of everything about running downloads. It lives outside of the UI,
 * so progress survives activity recreation, and it keeps [DownloadService] running
 * (foreground service) for as long as at least one download is active.
 */
object DownloadTracker {
    val info = mutableStateMapOf<String, DownloadInfo>()
    val paused = mutableStateMapOf<String, Boolean>()

    private val downloaders = ConcurrentHashMap<String, Downloader>()
    private val names = ConcurrentHashMap<String, String>()
    private var lastNotify = 0L

    fun isActive(id: String) = names.containsKey(id)

    fun hasActive() = names.isNotEmpty()

    fun newDownloader(
        id: String,
        client: OkHttpClient,
    ): Downloader = Downloader(client).also { downloaders[id] = it }

    fun downloader(id: String): Downloader? = downloaders[id]

    @Synchronized
    fun begin(
        context: Context,
        id: String,
        name: String,
        total: Long,
    ) {
        val app = context.applicationContext
        val wasEmpty = names.isEmpty()
        names[id] = name
        paused.remove(id)
        info[id] = DownloadInfo(0L, total, 0f)
        if (wasEmpty) {
            ContextCompat.startForegroundService(app, android.content.Intent(app, DownloadService::class.java))
        }
        lastNotify = 0L
        notifyProgress(app, force = true)
    }

    fun update(
        context: Context,
        id: String,
        downloaded: Long,
        speed: Float,
    ) {
        val current = info[id] ?: return
        info[id] = current.copy(downloaded = downloaded, speed = if (paused[id] == true) 0f else speed)
        notifyProgress(context.applicationContext, force = false)
    }

    fun setPaused(
        context: Context,
        id: String,
        value: Boolean,
    ) {
        if (value) paused[id] = true else paused.remove(id)
        info[id]?.let { info[id] = it.copy(speed = 0f) }
        notifyProgress(context.applicationContext, force = true)
    }

    @Synchronized
    fun end(
        context: Context,
        id: String,
    ) {
        val app = context.applicationContext
        names.remove(id) ?: return
        info.remove(id)
        paused.remove(id)
        downloaders.remove(id)
        if (names.isEmpty()) {
            app.stopService(android.content.Intent(app, DownloadService::class.java))
        } else {
            notifyProgress(app, force = true)
        }
    }

    fun cancelAll() {
        downloaders.values.forEach { it.cancelAll() }
    }

    private fun notifyProgress(
        app: Context,
        force: Boolean,
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotify < 1000) return
        if (names.isEmpty()) return
        lastNotify = now
        val infos = names.keys.mapNotNull { info[it] }
        val downloaded = infos.sumOf { it.downloaded }
        val total = infos.sumOf { it.total }
        val speed = infos.sumOf { it.speed.toDouble() }.toFloat()
        val allPaused = names.keys.all { paused[it] == true }
        val title = if (names.size == 1) names.values.first() else "Downloading ${names.size} versions"
        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        val text =
            if (allPaused) {
                "Paused • $percent% (${formatBytes(downloaded)} / ${formatBytes(total)})"
            } else {
                "$percent% • ${formatBytes(downloaded)} / ${formatBytes(total)} • ${formatSpeed(speed)}"
            }
        DownloadNotifications.show(app, title, text, percent)
    }
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

fun formatSpeed(bytesPerSecond: Float): String = "%.2f MB/s".format(bytesPerSecond / 1024f / 1024f)

fun formatEta(seconds: Long): String =
    when {
        seconds < 0 -> "--"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
