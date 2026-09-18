package com.stb6.sap.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stb6.sap.BuildConfig
import com.stb6.sap.HotspotService
import com.stb6.sap.R
import com.stb6.sap.SapApplication
import com.stb6.sap.network.Phase
import com.stb6.sap.ui.settings.SectionTitle
import com.stb6.sap.ui.widgets.TimeoutItem
import com.stb6.sap.ui.settings.ValueRow
import com.stb6.sap.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotScreen(app: SapApplication, onStart: () -> Unit, onPermissions: () -> Unit, onClients: () -> Unit) {
    val config by app.settings.current.collectAsStateWithLifecycle()
    val state by app.hotspot.state.collectAsStateWithLifecycle()
    val loadFailed by app.settings.loadFailed.collectAsStateWithLifecycle()
    LaunchedEffect(config != null) { if (config != null) app.hotspot.refresh() }
    val focus = LocalFocusManager.current
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name), maxLines = 1)
                        Text(stringResource(if (!app.hotspot.supported) R.string.state_unsupported else when (state.phase) {
                            Phase.Off -> R.string.hotspot_off
                            Phase.Starting -> R.string.state_starting
                            Phase.On -> R.string.hotspot_on
                            Phase.Stopping -> R.string.state_stopping
                            Phase.Checking, Phase.Unknown -> R.string.state_unknown
                        }), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    val hotspotLabel = stringResource(R.string.hotspot_control)
                    Switch(checked = state.switchChecked,
                        enabled = state.phase != Phase.Starting && state.phase != Phase.Stopping && app.hotspot.supported,
                        onCheckedChange = { enabled ->
                            focus.clearFocus()
                            if (enabled) onStart() else HotspotService.command(app, HotspotService.STOP)
                        }, modifier = Modifier.padding(end = SWITCH_END_PADDING).semantics { contentDescription = hotspotLabel })
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val settings = config
        if (settings == null) {
            if (loadFailed) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                TextButton(onClick = app.settings::load) { Text(stringResource(R.string.retry)) }
            } else DelayedLoading(Modifier.fillMaxSize().padding(padding))
        } else Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = Dimens.SettingsMaxWidth).fillMaxWidth()
                    .padding(horizontal = Dimens.SettingsHorizontalPadding).padding(bottom = Dimens.ContentVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SettingsItemGap),
            ) {
                SectionTitle(stringResource(R.string.section_settings), divider = false)
                NetworkFields(settings, state, onChange = { transform ->
                    if (!app.hotspot.state.value.locked) app.settings.update(transform)
                }, onInvalidCharacter = { app.notify(UiText(it)) })
                TimeoutItem(label = stringResource(R.string.auto_close), hint = stringResource(R.string.auto_close_hint),
                    fieldLabel = stringResource(R.string.timeout_field_label), unit = stringResource(R.string.minutes_unit),
                    value = settings.timeoutMinutes, onChange = { value -> app.settings.update { it.copy(timeoutMinutes = value) } })
                SectionTitle(stringResource(R.string.section_status))
                ClientSection(state) { focus.clearFocus(); onClients() }
                SectionTitle(stringResource(R.string.section_permissions))
                PermissionItems(onPermissions)
                SectionTitle(stringResource(R.string.section_about))
                ValueRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
                ValueRow(stringResource(R.string.license), stringResource(R.string.app_license))
            }
        }
    }
}

private val SWITCH_END_PADDING = 16.dp
