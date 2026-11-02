package com.akres.launchly.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class Downloader(
    private val client: OkHttpClient,
) {
    private val downloads = mutableMapOf<String, DownloadTask>()
    private val scope = CoroutineScope(Dispatchers.IO)

    sealed class DownloadStatus {
        data class Progress(
            val downloadedSize: Long,
            val speed: Float,
        ) : DownloadStatus()

        data object AllFinished : DownloadStatus()

        data class Failure(
            val fileName: String,
            val failureReason: String,
        ) : DownloadStatus()

        data class Cancelled(
            val fileName: String,
        ) : DownloadStatus()

        data class FileDownloaded(
            val fileName: String,
        ) : DownloadStatus()
    }

    data class DownloadTask(
        val url: String,
        val file: File,
        var isCancelled: Boolean = false,
        var downloadJob: Job? = null,
    )

    fun startDownload(
        urlToFileNameMap: Map<String, String>,
        destinationDir: File,
        onStatus: (DownloadStatus) -> Unit,
    ) {
        destinationDir.mkdirs()
        val downloadedSizeMap = mutableMapOf<String, Long>()
        var successCount = 0
        var lastDownloadedSize = 0L
        var lastStatus = System.currentTimeMillis()
        var speed = 0f
        val lock = Any()
        urlToFileNameMap.forEach { (fileName, url) ->
            val file = File(destinationDir, "$fileName.temp")
            val task = DownloadTask(url, file)
            downloadedSizeMap[fileName] = 0
            downloads[fileName] = task

            task.downloadJob =
                scope.launch {
                    downloadFile(task) { status ->
                        when (status) {
                            is DownloadStatus.Progress -> {
                                val (downloadedSize, currentSpeed) =
                                    synchronized(lock) {
                                        downloadedSizeMap[fileName] = status.downloadedSize
                                        val total = downloadedSizeMap.values.sum()
                                        val currentTime = System.currentTimeMillis()
                                        val elapsed = currentTime - lastStatus
                                        if (elapsed >= 1000) {
                                            val instant = (total - lastDownloadedSize) * 1000f / elapsed
                                            speed = if (speed == 0f) instant else speed * 0.5f + instant * 0.5f
                                            lastStatus = currentTime
                                            lastDownloadedSize = total
                                        }
                                        total to speed
                                    }
                                onStatus(DownloadStatus.Progress(downloadedSize, currentSpeed))
                            }

                            is DownloadStatus.FileDownloaded -> {
                                file.renameTo(File(destinationDir, fileName))
                                val finished = synchronized(lock) { ++successCount == urlToFileNameMap.size }
                                onStatus(status)
                                if (finished) {
                                    onStatus(DownloadStatus.AllFinished)
                                }
                            }

                            is DownloadStatus.Failure -> {
                                if (status.failureReason.contains("cancelled", true)) {
                                    task.file.delete()
                                    onStatus(DownloadStatus.Cancelled(status.fileName))
                                } else {
                                    onStatus(status)
                                    cancelAll()
                                }
                            }

                            else -> {}
                        }
                    }
                }
        }
    }

    private suspend fun downloadFile(
        task: DownloadTask,
        onStatus: (DownloadStatus) -> Unit,
    ) {
        val request =
            Request
                .Builder()
                .url(task.url)
                .build()

        try {
            val ok = withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    onStatus(DownloadStatus.Failure(task.file.name, "HTTP error: ${response.code}"))
                    return@withContext false
                }

                val inputStream = response.body?.byteStream()
                val outputStream = task.file.outputStream()
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                if (inputStream == null) {
                    onStatus(DownloadStatus.Failure(task.file.name, "Failed to open input stream"))
                    return@withContext false
                }

                try {
                    while (!task.isCancelled) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break

                        totalBytesRead += bytesRead
                        outputStream.write(buffer, 0, bytesRead)

                        onStatus(DownloadStatus.Progress(totalBytesRead, 0f))
                    }
                } finally {
                    inputStream.close()
                    outputStream.close()
                }
                true
            }
            if (!ok) return
            if (task.isCancelled) {
                onStatus(DownloadStatus.Failure(task.file.name, "cancelled"))
                return
            }
            onStatus(DownloadStatus.FileDownloaded(task.file.name))
        } catch (e: Exception) {
            val reason = if (task.isCancelled) "cancelled" else e.message ?: "Unknown error"
            onStatus(DownloadStatus.Failure(task.file.name, reason))
        }
    }

    fun cancelAll() {
        downloads.values.forEach { task ->
            task.isCancelled = true
            task.downloadJob?.cancel()
        }
    }
}
