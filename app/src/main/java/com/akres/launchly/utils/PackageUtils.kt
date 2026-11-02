package com.akres.launchly.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import ru.solrudev.ackpine.DisposableSubscriptionContainer
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.parameters.InstallParameters
import ru.solrudev.ackpine.installer.parameters.InstallerType
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import ru.solrudev.ackpine.uninstaller.UninstallFailure
import ru.solrudev.ackpine.uninstaller.parameters.UninstallParameters
import java.io.File
import java.util.UUID

const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

fun getInstalledPackageInfo(
    context: Context,
    packageName: String,
    flags: Int = 0,
): PackageInfo =
    if (SDK_INT >= TIRAMISU) {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        context.packageManager.getPackageInfo(packageName, flags)
    }

fun installPackage(
    context: Context,
    apks: Array<File>,
    onStatus: (SessionResult) -> Unit,
) {
    val packageInstaller = PackageInstaller.getInstance(context)
    val session =
        packageInstaller.createSession(
            InstallParameters
                .Builder(apks.map { Uri.fromFile(it) })
                .setConfirmation(Confirmation.IMMEDIATE)
                .setInstallerType(InstallerType.SESSION_BASED)
                .build(),
        )
    val subscription = DisposableSubscriptionContainer()
    session.addProgressListener(subscription) { _, progress ->
        onStatus(SessionResult.Progress(progress.progress))
    }
    session.addStateListener(
        subscription,
        object : Session.TerminalStateListener<InstallFailure>(session) {
            override fun onSuccess(sessionId: UUID) {
                onStatus(SessionResult.Success)
            }

            override fun onFailure(
                sessionId: UUID,
                failure: InstallFailure,
            ) {
                onStatus(SessionResult.Failure(failure.message ?: "Unknown error"))
            }

            override fun onCancelled(sessionId: UUID) {
                onStatus(SessionResult.Cancelled)
            }
        },
    )
}

fun uninstallPackage(
    context: Context,
    packageName: String,
    onStatus: (SessionResult) -> Unit,
) {
    val packageInstaller = PackageUninstaller.getInstance(context)
    val session =
        packageInstaller.createSession(
            UninstallParameters
                .Builder(packageName)
                .setConfirmation(Confirmation.IMMEDIATE)
                .build(),
        )
    session.addStateListener(
        DisposableSubscriptionContainer(),
        object : Session.TerminalStateListener<UninstallFailure>(session) {
            override fun onSuccess(sessionId: UUID) {
                onStatus(SessionResult.Success)
            }

            override fun onFailure(
                sessionId: UUID,
                failure: UninstallFailure,
            ) {
                onStatus(SessionResult.Failure("Unknown error"))
            }

            override fun onCancelled(sessionId: UUID) {
                onStatus(SessionResult.Cancelled)
            }
        },
    )
}

sealed class SessionResult {
    data object Success : SessionResult()

    data class Failure(
        val message: String? = null,
    ) : SessionResult()

    data class Progress(
        val progress: Int,
    ) : SessionResult()

    data object Cancelled : SessionResult()
}
