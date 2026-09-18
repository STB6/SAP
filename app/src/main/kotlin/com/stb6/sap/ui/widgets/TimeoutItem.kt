package com.stb6.sap.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stb6.sap.ui.theme.Dimens

@Composable
fun TimeoutItem(label: String, hint: String, fieldLabel: String, unit: String, value: Int, onChange: (Int) -> Unit, range: IntRange = 0..9999) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val focus = LocalFocusManager.current
    val commit = {
        val parsed = text.toIntOrNull()?.takeIf { it in range } ?: 0
        text = parsed.toString()
        if (parsed != value) onChange(parsed)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.SettingsMultiLineMargin), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { input -> if (input.length <= range.last.toString().length && input.all { it.isDigit() }) text = input },
            label = { Text(fieldLabel) },
            suffix = { Text(unit) },
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.width(FIELD_WIDTH).onFocusChanged { if (!it.isFocused) commit() },
        )
    }
}

private val FIELD_GAP = 16.dp
private val FIELD_WIDTH = 140.dp
