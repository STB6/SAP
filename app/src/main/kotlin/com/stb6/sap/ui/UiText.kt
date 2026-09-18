package com.stb6.sap.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

data class UiText(@param:StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}

@Composable
fun UiText.text(): String = resolve(LocalContext.current)
