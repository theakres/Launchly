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
        data class OnProgress(
            val downloadedSize: Long,
            val speed: Float, // bytes per second
        ) : DownloadStatus()

        data class OnFinish(
            val files: List<File>,
        ) : DownloadStatus()

        data class OnFailure(
            val fileName: String,
            val failureReason: String,
        ) : DownloadStatus()

        data class OnCancel(
            val fileName: String,
        ) : DownloadStatus()

        data class OnDownloadSuccess(
            val fileName: String,
        ) : DownloadStatus()
    }

    data class DownloadTask(
        val url: String,
        val file: File,
        var progress: Int = 0,
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
                            is DownloadStatus.OnProgress -> {
                                val (downloadedSize, currentSpeed) =
                                    synchronized(lock) {
                                        downloadedSizeMap[fileName] = status.downloadedSize
                                        val total = downloadedSizeMap.values.sum()
                                        val currentTime = System.currentTimeMillis()
                                        val elapsed = currentTime - lastStatus
                                        if (elapsed >= 1000) {
                                            // bytes per second, smoothed so the number doesn't jump around
                                            val instant = (total - lastDownloadedSize) * 1000f / elapsed
                                            speed = if (speed == 0f) instant else speed * 0.5f + instant * 0.5f
                                            lastStatus = currentTime
                                            lastDownloadedSize = total
                                        }
                                        total to speed
                                    }
                                onStatus(DownloadStatus.OnProgress(downloadedSize, currentSpeed))
                            }

                            is DownloadStatus.OnDownloadSuccess -> {
                                file.renameTo(File(destinationDir, fileName))
                                val finished = synchronized(lock) { ++successCount == urlToFileNameMap.size }
                                onStatus(status)
                                if (finished) {
                                    onStatus(DownloadStatus.OnFinish(downloads.values.map { it.file }))
                                }
                            }

                            is DownloadStatus.OnFailure -> {
                                if (status.failureReason.contains("cancelled", true)) {
                                    task.file.delete()
                                    onStatus(DownloadStatus.OnCancel(status.fileName))
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
                    onStatus(DownloadStatus.OnFailure(task.file.name, "HTTP error: ${response.code}"))
                    return@withContext false
                }

                val inputStream = response.body?.byteStream()
                val outputStream = task.file.outputStream()
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                if (inputStream == null) {
                    onStatus(DownloadStatus.OnFailure(task.file.name, "Failed to open input stream"))
                    return@withContext false
                }

                try {
                    while (!task.isCancelled) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break

                        totalBytesRead += bytesRead
                        outputStream.write(buffer, 0, bytesRead)

                        onStatus(DownloadStatus.OnProgress(totalBytesRead, 0f))
                    }
                } finally {
                    inputStream.close()
                    outputStream.close()
                }
                true
            }
            if (!ok) return
            if (task.isCancelled) {
                onStatus(DownloadStatus.OnFailure(task.file.name, "cancelled"))
                return
            }
            onStatus(DownloadStatus.OnDownloadSuccess(task.file.name))
        } catch (e: Exception) {
            // closing/cancelling mid-read throws IOException; that's a cancel, not a failure
            val reason = if (task.isCancelled) "cancelled" else e.message ?: "Unknown error"
            onStatus(DownloadStatus.OnFailure(task.file.name, reason))
        }
    }

    fun cancelAll() {
        downloads.values.forEach { task ->
            task.isCancelled = true
            task.downloadJob?.cancel()
        }
    }
}
