package com.stb6.sap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.stb6.sap.R
import com.stb6.sap.data.NetworkRules
import com.stb6.sap.data.Settings
import com.stb6.sap.network.HotspotState
import com.stb6.sap.network.Phase
import com.stb6.sap.ui.settings.SettingOption
import com.stb6.sap.ui.settings.SettingsChoice
import com.stb6.sap.ui.theme.Dimens
import com.stb6.sap.ui.widgets.copyToClipboard
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun NetworkFields(settings: Settings, state: HotspotState, onChange: ((Settings) -> Settings) -> Unit, onInvalidCharacter: (Int) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) visible = false }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // KeyboardType.Password keeps the IME from learning the passphrase, but Compose then reports the
    // field as a credential and password managers offer to save it. Cancel the autofill session instead.
    val autofill = LocalAutofillManager.current
    DisposableEffect(Unit) { onDispose { autofill?.cancel() } }
    LaunchedEffect(state.locked) { if (state.locked) focus.clearFocus() }
    val running = state.phase == Phase.On || state.phase == Phase.Stopping
    val ssid = if (running) state.ssid else settings.ssid
    val password = if (running) state.password else settings.password
    val suffix = if (running) ssid.removePrefix(Settings.SSID_PREFIX) else settings.suffix
    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.SettingsMultiLineMargin), verticalArrangement = Arrangement.spacedBy(Dimens.SettingsMultiLineMargin)) {
        OutlinedTextField(value = suffix, onValueChange = { value ->
            if (value.none { it.isISOControl() }) onChange { it.copy(suffix = value) } else onInvalidCharacter(R.string.ssid_characters)
        }, enabled = !state.locked, singleLine = true,
            label = { Text(stringResource(R.string.ssid)) }, prefix = { Text(Settings.SSID_PREFIX) },
            isError = !NetworkRules.validSsid(ssid),
            supportingText = if (!NetworkRules.validSsid(ssid)) ({ Text(stringResource(R.string.invalid_ssid)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { value ->
            if (value.all { it.code in 32..126 }) onChange { it.copy(password = value) } else onInvalidCharacter(R.string.password_characters)
        }, enabled = !state.locked, singleLine = true,
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { visible = !visible }) {
                Icon(painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password))
            } },
            isError = !NetworkRules.validPassword(password),
            supportingText = if (!NetworkRules.validPassword(password)) ({ Text(stringResource(R.string.invalid_password)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) autofill?.cancel() })
    }
    val ssidLabel = stringResource(R.string.copy_ssid)
    val passwordLabel = stringResource(R.string.copy_password)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
        OutlinedButton(onClick = { focus.clearFocus(); copyToClipboard(context, ssidLabel, ssid) }, modifier = Modifier.weight(1f)) { Text(ssidLabel) }
        OutlinedButton(onClick = { focus.clearFocus(); copyToClipboard(context, passwordLabel, password, true) }, modifier = Modifier.weight(1f)) { Text(passwordLabel) }
    }
    SettingsChoice(label = stringResource(R.string.band),
        options = listOf(SettingOption(2, stringResource(R.string.band_2)), SettingOption(5, stringResource(R.string.band_5))),
        selected = settings.band, enabled = !state.locked,
        onSelected = { selected -> focus.clearFocus(); onChange { it.copy(band = selected) } })
}


private val BUTTON_GAP = 8.dp
