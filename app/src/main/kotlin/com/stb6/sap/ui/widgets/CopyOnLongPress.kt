package com.stb6.sap.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.stb6.sap.ui.tapTarget
import com.stb6.sap.ui.theme.Dimens

@Composable
fun Modifier.copyOnLongPress(label: String, value: String?, sensitive: Boolean = false): Modifier {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    return tapTarget(enabled = value != null, bleed = Dimens.TapBleed, onLongClick = {
        if (value != null) {
            copyToClipboard(context, label, value, sensitive)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }) {}
}
