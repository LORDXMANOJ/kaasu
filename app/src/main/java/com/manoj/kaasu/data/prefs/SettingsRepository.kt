package com.manoj.kaasu.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.manoj.kaasu.core.SlotBoundaries
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
data class UserSettings(
    val onboardingDone: Boolean = false,
    val userName: String = "",
    val myVpa: String = "",
    val reportRecipient: String = "Dad",
    val dailyBudgetPaise: Long = 20_000,
    val todayOverrideDay: Long? = null,
    val todayOverridePaise: Long? = null,
    val countLentInBudget: Boolean = false,
    val morningStart: Int = 5,
    val afternoonStart: Int = 11,
    val eveningStart: Int = 16,
    val nightStart: Int = 21,
    val defaultUpiPackage: String? = null,
    val appLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean = false,
    val smsSyncEnabled: Boolean = false,
    val lastSmsScanAt: Long = 0,
    val untaggedSince: Long = 0,
    val openingBalancePaise: Long? = null,
    val openingBalanceAt: Long? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Look of the Home screen while the user tries out styles; "CLASSIC" is the original. */
    val homeStyle: String = "EMBER",
) {
    val slots: SlotBoundaries get() = SlotBoundaries(morningStart, afternoonStart, eveningStart, nightStart)
    val isLockConfigured: Boolean get() = appLockEnabled && pinHash != null && pinSalt != null

    fun budgetForDay(epochDay: Long): Long =
        if (todayOverrideDay == epochDay && todayOverridePaise != null) todayOverridePaise else dailyBudgetPaise
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val userName = stringPreferencesKey("user_name")
        val myVpa = stringPreferencesKey("my_vpa")
        val reportRecipient = stringPreferencesKey("report_recipient")
        val dailyBudget = longPreferencesKey("daily_budget")
        val overrideDay = longPreferencesKey("override_day")
        val overridePaise = longPreferencesKey("override_paise")
        val countLent = booleanPreferencesKey("count_lent")
        val morningStart = intPreferencesKey("morning_start")
        val afternoonStart = intPreferencesKey("afternoon_start")
        val eveningStart = intPreferencesKey("evening_start")
        val nightStart = intPreferencesKey("night_start")
        val defaultUpi = stringPreferencesKey("default_upi")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val biometric = booleanPreferencesKey("biometric")
        val smsSync = booleanPreferencesKey("sms_sync")
        val lastSmsScan = longPreferencesKey("last_sms_scan")
        val untaggedSince = longPreferencesKey("untagged_since")
        val openingBalance = longPreferencesKey("opening_balance")
        val openingBalanceAt = longPreferencesKey("opening_balance_at")
        val theme = stringPreferencesKey("theme")
        val homeStyle = stringPreferencesKey("home_style")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            onboardingDone = this[Keys.onboardingDone] ?: false,
            userName = this[Keys.userName] ?: "",
            myVpa = this[Keys.myVpa] ?: "",
            reportRecipient = this[Keys.reportRecipient] ?: defaults.reportRecipient,
            dailyBudgetPaise = this[Keys.dailyBudget] ?: defaults.dailyBudgetPaise,
            todayOverrideDay = this[Keys.overrideDay],
            todayOverridePaise = this[Keys.overridePaise],
            countLentInBudget = this[Keys.countLent] ?: false,
            morningStart = this[Keys.morningStart] ?: defaults.morningStart,
            afternoonStart = this[Keys.afternoonStart] ?: defaults.afternoonStart,
            eveningStart = this[Keys.eveningStart] ?: defaults.eveningStart,
            nightStart = this[Keys.nightStart] ?: defaults.nightStart,
            defaultUpiPackage = this[Keys.defaultUpi],
            appLockEnabled = this[Keys.lockEnabled] ?: false,
            pinHash = this[Keys.pinHash],
            pinSalt = this[Keys.pinSalt],
            biometricEnabled = this[Keys.biometric] ?: false,
            smsSyncEnabled = this[Keys.smsSync] ?: false,
            lastSmsScanAt = this[Keys.lastSmsScan] ?: 0,
            untaggedSince = this[Keys.untaggedSince] ?: 0,
            openingBalancePaise = this[Keys.openingBalance],
            openingBalanceAt = this[Keys.openingBalanceAt],
            theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            homeStyle = this[Keys.homeStyle] ?: "EMBER",
        )
    }

    suspend fun completeOnboarding(name: String, dailyBudgetPaise: Long) = context.dataStore.edit {
        it[Keys.userName] = name.trim()
        it[Keys.dailyBudget] = dailyBudgetPaise
        it[Keys.onboardingDone] = true
        if (it[Keys.untaggedSince] == null) it[Keys.untaggedSince] = System.currentTimeMillis()
    }

    suspend fun setUserName(value: String) = context.dataStore.edit { it[Keys.userName] = value.trim() }
    suspend fun setMyVpa(value: String) = context.dataStore.edit { it[Keys.myVpa] = value.trim() }
    suspend fun setReportRecipient(value: String) = context.dataStore.edit { it[Keys.reportRecipient] = value.trim() }
    suspend fun setDailyBudget(paise: Long) = context.dataStore.edit { it[Keys.dailyBudget] = paise }
    suspend fun setCountLent(value: Boolean) = context.dataStore.edit { it[Keys.countLent] = value }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setHomeStyle(name: String) = context.dataStore.edit { it[Keys.homeStyle] = name }

    /** Forget everything: Kaasu starts again from setup. */
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
    suspend fun setBiometric(value: Boolean) = context.dataStore.edit { it[Keys.biometric] = value }
    suspend fun setLastSmsScan(value: Long) = context.dataStore.edit { it[Keys.lastSmsScan] = value }

    suspend fun setTodayOverride(epochDay: Long, paise: Long?) = context.dataStore.edit {
        if (paise == null) {
            it.remove(Keys.overrideDay)
            it.remove(Keys.overridePaise)
        } else {
            it[Keys.overrideDay] = epochDay
            it[Keys.overridePaise] = paise
        }
    }

    suspend fun setSlots(slots: SlotBoundaries) = context.dataStore.edit {
        it[Keys.morningStart] = slots.morningStart
        it[Keys.afternoonStart] = slots.afternoonStart
        it[Keys.eveningStart] = slots.eveningStart
        it[Keys.nightStart] = slots.nightStart
    }

    suspend fun setDefaultUpi(packageName: String?) = context.dataStore.edit {
        if (packageName == null) it.remove(Keys.defaultUpi) else it[Keys.defaultUpi] = packageName
    }

    suspend fun setPin(hash: String, salt: String) = context.dataStore.edit {
        it[Keys.pinHash] = hash
        it[Keys.pinSalt] = salt
        it[Keys.lockEnabled] = true
    }

    suspend fun disableLock() = context.dataStore.edit {
        it[Keys.lockEnabled] = false
        it[Keys.biometric] = false
        it.remove(Keys.pinHash)
        it.remove(Keys.pinSalt)
    }

    suspend fun setSmsSync(enabled: Boolean) = context.dataStore.edit { it[Keys.smsSync] = enabled }

    suspend fun setOpeningBalance(paise: Long?) = context.dataStore.edit {
        if (paise == null) {
            it.remove(Keys.openingBalance)
            it.remove(Keys.openingBalanceAt)
        } else {
            it[Keys.openingBalance] = paise
            it[Keys.openingBalanceAt] = System.currentTimeMillis()
        }
    }

    /** Restores the user-facing settings from a backup; security and sync bookkeeping are kept. */
    suspend fun restore(from: UserSettings) = context.dataStore.edit {
        it[Keys.onboardingDone] = true
        it[Keys.userName] = from.userName
        it[Keys.myVpa] = from.myVpa
        it[Keys.reportRecipient] = from.reportRecipient
        it[Keys.dailyBudget] = from.dailyBudgetPaise
        it[Keys.countLent] = from.countLentInBudget
        it[Keys.morningStart] = from.morningStart
        it[Keys.afternoonStart] = from.afternoonStart
        it[Keys.eveningStart] = from.eveningStart
        it[Keys.nightStart] = from.nightStart
        it[Keys.theme] = from.theme.name
        it[Keys.untaggedSince] = from.untaggedSince
        from.defaultUpiPackage?.let { pkg -> it[Keys.defaultUpi] = pkg }
        from.openingBalancePaise?.let { v -> it[Keys.openingBalance] = v }
        from.openingBalanceAt?.let { v -> it[Keys.openingBalanceAt] = v }
    }
}
