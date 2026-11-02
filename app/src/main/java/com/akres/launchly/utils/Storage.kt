package com.akres.launchly.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

object Storage {
    fun rootDir(): File = File(Environment.getExternalStorageDirectory(), "Launchly")

    fun versionDir(id: String): File = File(rootDir(), id)

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun requestStorageAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent =
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        } else {
            (context as? Activity)?.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    fun migrateLegacyVersions(context: Context) {
        val legacy = File(context.filesDir, "versions")
        val dirs = legacy.listFiles { f -> f.isDirectory } ?: return
        dirs.forEach { dir ->
            val target = versionDir(dir.name)
            if (!target.exists() && dir.copyRecursively(target, overwrite = true)) {
                dir.deleteRecursively()
            }
        }
    }
}
