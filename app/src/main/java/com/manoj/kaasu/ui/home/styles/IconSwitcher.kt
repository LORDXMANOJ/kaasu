package com.manoj.kaasu.ui.home.styles

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Shows the launcher icon that matches the chosen style (one activity-alias per style, one enabled at a time). */
object IconSwitcher {
    fun apply(context: Context, style: HomeStyle) {
        val pm = context.packageManager
        val wanted = alias(context, style)
        // Enable the new icon before disabling the old one so the app never has zero launcher entries.
        set(pm, wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        HomeStyle.entries.filter { it != style }.forEach { set(pm, alias(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
    }

    private fun alias(context: Context, style: HomeStyle) = ComponentName(context.packageName, "com.manoj.kaasu.Icon_${style.name}")

    private fun set(pm: PackageManager, component: ComponentName, state: Int) {
        val current = runCatching { pm.getComponentEnabledSetting(component) }.getOrNull() ?: return
        val isDefaultEnabled = component.className.endsWith("Icon_EMBER")
        val effectiveOn = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && isDefaultEnabled)
        val wantOn = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (effectiveOn == wantOn) return
        runCatching { pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
    }
}
