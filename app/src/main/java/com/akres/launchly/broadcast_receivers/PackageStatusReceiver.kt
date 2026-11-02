package com.akres.launchly.broadcast_receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageStatusReceiver(
    private val onPackageRemoved: (String) -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_PACKAGE_REMOVED) return
        intent.data?.schemeSpecificPart?.let(onPackageRemoved)
    }
}
