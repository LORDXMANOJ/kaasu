package com.manoj.kaasu.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File

fun Context.findActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

fun Context.shareText(text: String, title: String = "Share") {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(intent, title))
}

/** Opens a WhatsApp chat with this Indian number with the message typed in, falling back to the share sheet. */
fun Context.sendWhatsApp(phone: String?, text: String) {
    val digits = phone?.filter { it.isDigit() }.orEmpty()
    if (digits.length < 10) {
        shareText(text, "Send reminder")
        return
    }
    val number = if (digits.length == 10) "91$digits" else digits
    val uri = Uri.parse("https://wa.me/$number?text=" + Uri.encode(text))
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        shareText(text, "Send reminder")
    }
}

fun Context.shareImage(file: File, title: String, caption: String? = null) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (caption != null) intent.putExtra(Intent.EXTRA_TEXT, caption)
    startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    startActivity(intent)
}

/** True when the user has allowed Kaasu to read (UPI-app) notifications. */
fun Context.hasNotificationAccess(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

fun Context.openNotificationAccessSettings() {
    try {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        openAppSettings()
    }
}

fun Context.sharedCacheDir(): File = File(cacheDir, "shared").apply { mkdirs() }
