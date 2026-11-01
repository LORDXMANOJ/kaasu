package com.manoj.kaasu.data.notif

import android.content.Context
import com.manoj.kaasu.core.Dates

/** What the UPI-alert reader has done, shown under its switch in Settings. Stays on this phone. */
object NotifStatus {
    private const val FILE = "notif_status"
    private const val KEEP = 6

    data class Snapshot(val connectedAt: Long, val seen: Int, val recorded: Int, val recent: List<String>) {
        val last: String? get() = recent.firstOrNull()
    }

    @Synchronized
    fun read(context: Context): Snapshot {
        val p = prefs(context)
        val recent = p.getString("recent", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        return Snapshot(p.getLong("connectedAt", 0), p.getInt("seen", 0), p.getInt("recorded", 0), recent)
    }

    @Synchronized
    fun connected(context: Context, upiInPanel: Int) {
        prefs(context).edit().putLong("connectedAt", Dates.now()).apply()
        push(context, "Connected · $upiInPanel UPI alerts in panel")
    }

    @Synchronized
    fun seen(context: Context, result: String) {
        val p = prefs(context)
        p.edit().putInt("seen", p.getInt("seen", 0) + 1).apply()
        push(context, result)
    }

    @Synchronized
    fun recorded(context: Context) {
        val p = prefs(context)
        p.edit().putInt("recorded", p.getInt("recorded", 0) + 1).apply()
    }

    private fun push(context: Context, line: String) {
        val p = prefs(context)
        val old = p.getString("recent", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        val time = Dates.time(Dates.now())
        p.edit().putString("recent", (listOf("$time  ${line.replace('\n', ' ')}") + old).take(KEEP).joinToString("\n")).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
