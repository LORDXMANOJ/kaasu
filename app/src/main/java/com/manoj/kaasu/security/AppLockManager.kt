package com.manoj.kaasu.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locks the app with PIN/fingerprint when it comes back after a while.
 * Coming back from a UPI app (or camera/gallery/share/file pickers) within the grace window never
 * locks, so paying is never followed by a prompt.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val appScope: AppScope,
) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile private var enabled = false
    private var backgroundedAt = 0L
    private var externalLaunchAt = 0L
    private var started = false

    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appScope.launch {
            var first = true
            settings.settings.collect { s ->
                enabled = s.isLockConfigured
                if (first) {
                    _locked.value = enabled
                    _ready.value = true
                    first = false
                } else if (!enabled) {
                    _locked.value = false
                }
            }
        }
    }

    /** Call right before opening another app on purpose. */
    fun markExternalLaunch() {
        externalLaunchAt = System.currentTimeMillis()
    }

    fun unlock() {
        _locked.value = false
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val now = System.currentTimeMillis()
        val cameFromOurLaunch = externalLaunchAt != 0L && now - externalLaunchAt < EXTERNAL_GRACE_MS
        externalLaunchAt = 0L
        if (!enabled || backgroundedAt == 0L || cameFromOurLaunch) return
        if (now - backgroundedAt > RELOCK_AFTER_MS) _locked.value = true
    }

    companion object {
        private const val RELOCK_AFTER_MS = 60_000L
        private const val EXTERNAL_GRACE_MS = 30 * 60_000L
    }
}
