package com.manoj.kaasu

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.prefs.ThemeMode
import com.manoj.kaasu.data.sms.SyncScheduler
import com.manoj.kaasu.security.AppLockManager
import com.manoj.kaasu.ui.AppRoot
import com.manoj.kaasu.ui.theme.KaasuTheme
import com.manoj.kaasu.ui.home.styles.HomeStyle
import com.manoj.kaasu.ui.theme.paletteFor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var syncScheduler: SyncScheduler

    /** A upi:// link opened from another app (Lens, browser...) waiting to be shown in the pay screen. */
    private val incomingUpi = MutableStateFlow<String?>(null)

    /** The style whose icon the launcher should show. */
    private var pendingIconStyle: HomeStyle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        if (savedInstanceState == null) readUpiLink(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val lockReady by lockManager.ready.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            if (!lockReady) return@setContent
            SideEffect { ready = true }
            val palette = paletteFor(HomeStyle.from(current.homeStyle))
            val dark = palette.isDark
            // The launcher icon follows the style; it is swapped when the app goes to the background (see onStop).
            SideEffect { pendingIconStyle = HomeStyle.from(current.homeStyle) }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            KaasuTheme(palette = palette) {
                AppRoot(
                    settings = current,
                    lockManager = lockManager,
                    incomingUpi = incomingUpi,
                    onIncomingHandled = { incomingUpi.value = null },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncScheduler.syncSoon()
        com.manoj.kaasu.data.notif.NotificationIngestor.requestRebind(this)
    }

    /**
     * Swapping the launcher alias closes the task it was opened from, so the icon is only changed once
     * the user has left the app; next time they open Kaasu it has the new icon.
     */
    override fun onStop() {
        super.onStop()
        val style = pendingIconStyle ?: return
        if (isChangingConfigurations) return
        com.manoj.kaasu.ui.home.styles.IconSwitcher.apply(applicationContext, style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readUpiLink(intent)
    }

    private fun readUpiLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN)?.let { target ->
            incomingUpi.value = OPEN_PREFIX + target
            intent.removeExtra(EXTRA_OPEN)
            return
        }
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && data.scheme.equals("upi", ignoreCase = true)) {
            incomingUpi.value = data.toString()
        }
    }

    companion object {
        /** Home-screen widget buttons open a screen directly: "scan" or "people". */
        const val EXTRA_OPEN = "com.manoj.kaasu.OPEN"
        const val OPEN_PREFIX = "open:"
        const val OPEN_SCAN = "scan"
        const val OPEN_PEOPLE = "people"
        const val OPEN_HISTORY = "history"
        const val OPEN_REPORTS = "reports"
    }
}
