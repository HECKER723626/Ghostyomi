package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.screen.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Screen
import org.koin.androidx.compose.koinViewModel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

class P2PSettingsScreen : Screen() {
    
    @Composable
    override fun Content() {
        val viewModel: P2PSettingsScreenModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        
        Scaffold(
            topBar = {
                eu.kanade.presentation.components.AppBar(
                    title = stringResource(AYMR.strings.p2p_settings),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Enable P2P Switch
                SwitchPreferenceWidget(
                    title = stringResource(AYMR.strings.p2p_enable),
                    subtitle = stringResource(AYMR.strings.p2p_enable_summary),
                    checked = state.isEnabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                )
                
                // P2P Status Section
                if (state.isEnabled) {
                    // Peer ID Display
                    if (state.peerId.isNotEmpty()) {
                        SettingsSection(
                            title = stringResource(AYMR.strings.p2p_status),
                        ) {
                            Text(
                                text = "${stringResource(AYMR.strings.p2p_peer_id)}: ${state.peerId}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    
                    // Network Status
                    SettingsSection(
                        title = stringResource(AYMR.strings.p2p_network_status),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "${stringResource(AYMR.strings.p2p_listening_addresses)}: ${state.listeningAddresses}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "${stringResource(AYMR.strings.p2p_connected_peers)}: ${state.connectedPeers}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "${stringResource(AYMR.strings.p2p_dht_providers)}: ${state.dhtProviders}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    
                    // Data Usage
                    SettingsSection(
                        title = stringResource(AYMR.strings.p2p_data_usage),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "${stringResource(AYMR.strings.p2p_data_uploaded)}: ${state.uploadedBytes}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "${stringResource(AYMR.strings.p2p_data_downloaded)}: ${state.downloadedBytes}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    
                    // Bandwidth Limit
                    SwitchPreferenceWidget(
                        title = stringResource(AYMR.strings.p2p_bandwidth_limit),
                        subtitle = stringResource(AYMR.strings.p2p_bandwidth_limit_summary),
                        checked = state.hasBandwidthLimit,
                        onCheckedChange = { viewModel.setBandwidthLimit(it) },
                    )
                    
                    // Auto-connect on WiFi
                    SwitchPreferenceWidget(
                        title = stringResource(AYMR.strings.p2p_wifi_only),
                        subtitle = stringResource(AYMR.strings.p2p_wifi_only_summary),
                        checked = state.wifiOnly,
                        onCheckedChange = { viewModel.setWifiOnly(it) },
                    )
                    
                    // Announce to DHT
                    SwitchPreferenceWidget(
                        title = stringResource(AYMR.strings.p2p_announce),
                        subtitle = stringResource(AYMR.strings.p2p_announce_summary),
                        checked = state.autoAnnounce,
                        onCheckedChange = { viewModel.setAutoAnnounce(it) },
                    )
                    
                    // Discover Peers
                    SwitchPreferenceWidget(
                        title = stringResource(AYMR.strings.p2p_discover),
                        subtitle = stringResource(AYMR.strings.p2p_discover_summary),
                        checked = state.autoDiscover,
                        onCheckedChange = { viewModel.setAutoDiscover(it) },
                    )
                    
                    // Manual Sync Button
                    SettingsSection(
                        title = stringResource(AYMR.strings.p2p_actions),
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.runManualSync() },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Text(stringResource(AYMR.strings.p2p_run_sync))
                        }
                    }
                }
                
                // Info Section
                SettingsSection(
                    title = stringResource(AYMR.strings.p2p_info),
                ) {
                    Text(
                        text = stringResource(AYMR.strings.p2p_info_text),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}
