package com.manoj.kaasu.data.upi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class UpiApp(val packageName: String, val label: String, val icon: Drawable?)

@Singleton
class UpiAppResolver @Inject constructor(@ApplicationContext private val context: Context) {

    private val preferredOrder = listOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "in.amazon.mShop.android.shopping",
        "com.dreamplug.androidapp",
        "com.sbi.lotusintouch",
        "com.sbi.upi",
    )

    fun installedApps(): List<UpiApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return infos
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName && !it.startsWith("com.manoj.kaasu") }
            .map { pkg ->
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                UpiApp(
                    packageName = pkg,
                    label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                    icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull(),
                )
            }
            .sortedWith(compareBy({ preferredOrder.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.label }))
    }

    fun label(packageName: String?): String? {
        if (packageName == null) return null
        val pm = context.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrNull()
    }

    fun isInstalled(packageName: String): Boolean = installedApps().any { it.packageName == packageName }

    fun payIntent(link: String, packageName: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(packageName)

    companion object {
        /** Reads every shape of response the different UPI apps send back. */
        fun responseText(data: Intent?): String? {
            if (data == null) return null
            data.getStringExtra("response")?.let { return it }
            val extras: Bundle = data.extras ?: return null
            val parts = extras.keySet().mapNotNull { key ->
                @Suppress("DEPRECATION")
                val value = extras.get(key)?.toString() ?: return@mapNotNull null
                "$key=$value"
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString("&")
        }
    }
}
