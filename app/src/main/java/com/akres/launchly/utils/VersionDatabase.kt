package com.akres.launchly.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import java.io.Serializable

class SavedVersionsViewModel : ViewModel() {
    private val _downloadedVersions = mutableStateListOf<SavedVersion>()
    val downloadedVersions: SnapshotStateList<SavedVersion> get() = _downloadedVersions

    fun reloadSavedVersions(context: Context) {
        val versionDatabase = VersionDatabase(context)
        _downloadedVersions.clear()
        _downloadedVersions.addAll(versionDatabase.getAllVersions())
    }

    fun indexOfVersion(id: String): Int = downloadedVersions.indexOfFirst { it.installationId == id }
}

data class SavedVersion(
    val installationId: String,
    val name: String,
    val versionCode: String,
    val versionName: String,
    val architecture: Architecture,
    val status: VersionStatus,
    val dateModified: Long,
) : Serializable

enum class Architecture(
    val arch: String,
) {
    ARM64("arm64-v8a"),
    ARM("armeabi-v7a"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromString(arch: String): Architecture = entries.firstOrNull { it.arch == arch } ?: UNKNOWN
    }
}

enum class VersionStatus {
    INSTALLED,
    DOWNLOADING,
    NOT_INSTALLED,
    NOT_DOWNLOADED,
}

class VersionDatabase(
    context: Context,
) : SQLiteOpenHelper(context, "version.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE versions (
                id TEXT PRIMARY KEY,
                name TEXT,
                versionCode TEXT,
                versionName TEXT,
                architecture TEXT,
                status TEXT,
                dateModified INTEGER
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db.execSQL("DROP TABLE IF EXISTS versions")
        onCreate(db)
    }

    fun insertVersion(savedVersion: SavedVersion) {
        writableDatabase.use { db ->
            val values =
                ContentValues().apply {
                    put("id", savedVersion.installationId)
                    put("name", savedVersion.name)
                    put("versionCode", savedVersion.versionCode)
                    put("versionName", savedVersion.versionName)
                    put("architecture", savedVersion.architecture.name)
                    put("status", savedVersion.status.name)
                    put("dateModified", savedVersion.dateModified)
                }
            db.insertWithOnConflict("versions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun removeVersion(id: String) {
        writableDatabase.use { db ->
            db.delete("versions", "id = ?", arrayOf(id))
        }
    }

    fun getAllVersions(): List<SavedVersion> {
        val savedVersions = mutableListOf<SavedVersion>()
        readableDatabase.use { db ->
            db.query("versions", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    savedVersions.add(
                        SavedVersion(
                            installationId = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            versionCode = cursor.getString(cursor.getColumnIndexOrThrow("versionCode")),
                            versionName = cursor.getString(cursor.getColumnIndexOrThrow("versionName")),
                            architecture =
                                runCatching {
                                    Architecture.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("architecture")))
                                }.getOrDefault(Architecture.UNKNOWN),
                            status = VersionStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                            dateModified = cursor.getLong(cursor.getColumnIndexOrThrow("dateModified")),
                        ),
                    )
                }
            }
        }
        return savedVersions
    }

    fun updateVersion(savedVersion: SavedVersion) {
        writableDatabase.use { db ->
            val values =
                ContentValues().apply {
                    put("name", savedVersion.name)
                    put("versionCode", savedVersion.versionCode)
                    put("versionName", savedVersion.versionName)
                    put("architecture", savedVersion.architecture.name)
                    put("status", savedVersion.status.name)
                    put("dateModified", savedVersion.dateModified)
                }
            db.update("versions", values, "id = ?", arrayOf(savedVersion.installationId))
        }
    }
}
