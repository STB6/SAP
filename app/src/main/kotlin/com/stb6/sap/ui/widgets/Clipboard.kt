package com.stb6.sap.ui.widgets

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

fun copyToClipboard(context: Context, label: String, value: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, value)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
