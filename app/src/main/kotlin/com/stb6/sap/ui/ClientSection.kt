package com.stb6.sap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stb6.sap.R
import com.stb6.sap.network.HotspotState
import com.stb6.sap.ui.settings.ValueRow
import com.stb6.sap.ui.theme.Dimens
import com.stb6.sap.ui.widgets.copyOnLongPress

@Composable
fun ClientSection(state: HotspotState, onOpen: () -> Unit) {
    val addressLabel = stringResource(R.string.local_address)
    ValueRow(addressLabel, state.hostAddress ?: stringResource(R.string.unknown),
        modifier = Modifier.copyOnLongPress(addressLabel, state.hostAddress))
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
        .tapTarget(bleed = Dimens.TapBleed, onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.connected_devices), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(state.clients.size.toString(), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
