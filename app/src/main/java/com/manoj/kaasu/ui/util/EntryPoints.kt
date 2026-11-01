package com.manoj.kaasu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.manoj.kaasu.security.AppLockManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UiEntryPoint {
    fun lockManager(): AppLockManager
}

/** For screens that open other apps (gallery, share sheet, file picker) so coming back never asks for the PIN. */
@Composable
fun rememberLockManager(): AppLockManager {
    val context = LocalContext.current.applicationContext
    return remember { EntryPointAccessors.fromApplication(context, UiEntryPoint::class.java).lockManager() }
}
