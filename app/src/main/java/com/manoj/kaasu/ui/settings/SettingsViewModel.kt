package com.manoj.kaasu.ui.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.kaasu.core.PinHasher
import com.manoj.kaasu.core.SlotBoundaries
import com.manoj.kaasu.data.backup.BackupManager
import com.manoj.kaasu.data.prefs.SettingsRepository
import com.manoj.kaasu.data.prefs.ThemeMode
import com.manoj.kaasu.data.prefs.UserSettings
import com.manoj.kaasu.data.repo.SmsRepository
import com.manoj.kaasu.data.sms.SmsInboxReader
import com.manoj.kaasu.data.sms.SmsSyncManager
import com.manoj.kaasu.data.upi.UpiApp
import com.manoj.kaasu.data.upi.UpiAppResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class SettingsState(
    val settings: UserSettings = UserSettings(),
    val upiApps: List<UpiApp> = emptyList(),
    val smsPermission: Boolean = false,
    val smsEventCount: Int = 0,
    val busy: Boolean = false,
) {
    val defaultAppLabel: String?
        get() = upiApps.firstOrNull { it.packageName == settings.defaultUpiPackage }?.label
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val upiResolver: UpiAppResolver,
    private val smsReader: SmsInboxReader,
    private val smsSync: SmsSyncManager,
    private val backup: BackupManager,
    sms: SmsRepository,
) : ViewModel() {
    private val apps = MutableStateFlow<List<UpiApp>>(emptyList())
    private val permission = MutableStateFlow(smsReader.hasPermission())
    private val busy = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<SettingsState> = combine(repo.settings, apps, permission, sms.count, busy) { s, a, p, c, b ->
        SettingsState(s, a, p, c, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init {
        viewModelScope.launch { apps.value = withContext(Dispatchers.IO) { upiResolver.installedApps() } }
    }

    fun refreshPermission() {
        permission.value = smsReader.hasPermission()
    }

    fun setName(v: String) = launch { repo.setUserName(v) }
    fun setMyVpa(v: String) = launch { repo.setMyVpa(v) }
    fun setRecipient(v: String) = launch { repo.setReportRecipient(v.ifBlank { "Dad" }) }
    fun setDailyBudget(v: Long) = launch { repo.setDailyBudget(v) }
    fun setCountLent(v: Boolean) = launch { repo.setCountLent(v) }
    fun setTheme(v: ThemeMode) = launch { repo.setTheme(v) }
    fun setHomeStyle(v: String) = launch { repo.setHomeStyle(v) }
    fun setDefaultUpi(pkg: String?) = launch { repo.setDefaultUpi(pkg) }
    fun setBalance(v: Long?) = launch { repo.setOpeningBalance(v) }
    fun setBiometric(v: Boolean) = launch { repo.setBiometric(v) }
    fun disableLock() = launch { repo.disableLock() }

    fun setSlots(slots: SlotBoundaries) = launch {
        if (slots.isValid()) repo.setSlots(slots) else _messages.send("Times must go morning → afternoon → evening → night")
    }

    fun setPin(pin: String) = launch {
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        repo.setPin(hash, salt)
        _messages.send("PIN saved")
    }

    fun setSmsEnabled(enabled: Boolean) = launch {
        refreshPermission()
        repo.setSmsSync(enabled && permission.value)
        if (enabled && permission.value) {
            val found = smsSync.sync()
            _messages.send(if (found > 0) "Found $found bank transactions" else "Bank SMS is on")
        }
    }

    fun rescan() = work {
        val found = smsSync.sync(lookBackDays = RESCAN_DAYS)
        _messages.send(if (found > 0) "Found $found new bank transactions" else "No new bank transactions found")
    }

    fun backupTo(uri: Uri) = work {
        val summary = backup.export(uri)
        _messages.send("Backed up ${summary.payments} payments")
    }

    fun restoreFrom(uri: Uri) = work {
        val summary = backup.restore(uri)
        _messages.send("Restored ${summary.payments} payments and ${summary.contacts} people")
    }

    fun exportCsv(uri: Uri) = work {
        val count = backup.exportCsv(uri)
        _messages.send("Exported $count payments")
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { block() }.onFailure { _messages.send(it.message ?: "Something went wrong") }
            busy.value = false
        }
    }

    companion object {
        private const val RESCAN_DAYS = 30
    }
}
