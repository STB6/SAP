package com.stb6.sap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.stb6.sap.R
import com.stb6.sap.network.Client
import com.stb6.sap.ui.theme.Dimens
import com.stb6.sap.ui.widgets.copyOnLongPress
import com.stb6.sap.ui.widgets.listScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedDevicesScreen(clients: List<Client>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val listState = rememberLazyListState()
    val macUnknown = stringResource(R.string.mac_unknown)
    val ipUnknown = stringResource(R.string.ip_unknown)
    val ipLabel = stringResource(R.string.ip_label)
    val macLabel = stringResource(R.string.mac_label)
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.connected_devices)) }, scrollBehavior = scroll) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .listScrollbar(listState, MaterialTheme.colorScheme.outline),
            state = listState,
            contentPadding = PaddingValues(bottom = Dimens.ContentVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(clients, key = { index, client -> client.address.ifBlank { "#$index" } }) { _, client ->
                Row(Modifier.widthIn(max = Dimens.SettingsMaxWidth).fillMaxWidth()
                    .padding(horizontal = Dimens.SettingsHorizontalPadding), verticalAlignment = Alignment.CenterVertically) {
                    Text(client.address.ifBlank { macUnknown },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.SettingsRowMinHeight)
                            .copyOnLongPress(macLabel, client.address.takeIf { it.isNotBlank() })
                            .wrapContentHeight(Alignment.CenterVertically),
                        textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(client.ip ?: ipUnknown,
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.SettingsRowMinHeight)
                            .copyOnLongPress(ipLabel, client.ip)
                            .wrapContentHeight(Alignment.CenterVertically),
                        textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
