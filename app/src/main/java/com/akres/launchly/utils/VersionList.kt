
package com.akres.launchly.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Serializable

data class AvailableVersion(
    val code: Long,
    val name: String,
    val architecture: Architecture,
) : Serializable

class AvailableVersionsViewModel(
    private val arch: String,
) : ViewModel() {
    private val client = OkHttpClient()

    private val _versions = MutableStateFlow<List<AvailableVersion>>(emptyList())
    val versions: StateFlow<List<AvailableVersion>> get() = _versions

    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        loadVersions()
    }

    private fun loadVersions() {
        if (isLoading.value || _versions.value.isNotEmpty()) return
        isLoading.value = true
        viewModelScope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        val request =
                            Request
                                .Builder()
                                .url(
                                    "https://raw.githubusercontent.com/minecraft-linux/mcpelauncher-versiondb/refs/heads/master/versions.$arch.json.min",
                                ).build()
                        client.newCall(request).execute()
                    }

                if (result.isSuccessful) {
                    val jsonResponse = result.body?.string()
                    jsonResponse?.let {
                        val parsedData = parseVersions(it)
                        _versions.value = parsedData
                    }
                } else {
                    error.value = "Failed to fetch data: ${result.code}"
                }
                result.close()
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun parseVersions(jsonString: String): List<AvailableVersion> =
        jsonString
            .removeSurrounding("[[", "]]")
            .split("],[")
            .map {
                val (versionCode, versionName, channel) =
                    it
                        .split(",")
                        .map { part -> part.trim('"') }
                Triple(versionCode.toLong(), versionName, channel.toInt())
            }.filter { (code, _, type) -> type == 0 && code >= 871000500 }
            .map { (code, name, _) -> AvailableVersion(code, name, Architecture.fromString(arch)) }
            .reversed()
}
