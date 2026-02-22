package eu.kanade.presentation.more.settings.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.p2p.P2PHostManager
import eu.kanade.tachiyomi.data.p2p.P2PPreferences
import eu.kanade.tachiyomi.data.p2p.P2PWorker
import eu.kanade.tachiyomi.data.storage.StorageReservationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class P2PSettingsState(
    val isEnabled: Boolean = false,
    val peerId: String = "",
    val listeningAddresses: String = "N/A",
    val connectedPeers: Int = 0,
    val dhtProviders: Int = 0,
    val uploadedBytes: String = "0 B",
    val downloadedBytes: String = "0 B",
    val hasBandwidthLimit: Boolean = false,
    val wifiOnly: Boolean = true,
    val autoAnnounce: Boolean = true,
    val autoDiscover: Boolean = true,
)

class P2PSettingsScreenModel(
    application: Application,
    private val p2pPreferences: P2PPreferences,
    private val p2pHostManager: P2PHostManager,
    private val storageReservationManager: StorageReservationManager,
) : AndroidViewModel(application) {
    
    private val _state = MutableStateFlow(P2PSettingsState())
    val state: StateFlow<P2PSettingsState> = _state.asStateFlow()
    
    init {
        loadPreferences()
        refreshStatus()
    }
    
    private fun loadPreferences() {
        _state.update {
            it.copy(
                isEnabled = p2pPreferences.p2pEnabled().get(),
                hasBandwidthLimit = p2pPreferences.bandwidthLimitEnabled().get(),
                wifiOnly = p2pPreferences.wifiOnly().get(),
                autoAnnounce = p2pPreferences.autoAnnounce().get(),
                autoDiscover = p2pPreferences.autoDiscover().get(),
            )
        }
    }
    
    fun refreshStatus() {
        viewModelScope.launch {
            try {
                if (p2pHostManager.isInitialized()) {
                    val host = p2pHostManager.getHost()
                    _state.update { state ->
                        state.copy(
                            peerId = host.peerId.toBase58(),
                            listeningAddresses = host.listenAddresses().joinToString(", ") { it.toString() },
                            connectedPeers = p2pHostManager.getConnectedPeerCount(),
                            dhtProviders = p2pHostManager.getDhtProviderCount(),
                        )
                    }
                }
                
                // Update data usage
                val uploaded = p2pPreferences.totalUploaded().get()
                val downloaded = p2pPreferences.totalDownloaded().get()
                _state.update {
                    it.copy(
                        uploadedBytes = formatBytes(uploaded),
                        downloadedBytes = formatBytes(downloaded),
                    )
                }
            } catch (e: Exception) {
                // P2P not initialized yet
                _state.update { it.copy(peerId = "Not initialized") }
            }
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            p2pPreferences.p2pEnabled().set(enabled)
            _state.update { it.copy(isEnabled = enabled) }
            
            if (enabled) {
                // Start P2P host
                p2pHostManager.start()
                // Schedule periodic work
                P2PWorker.schedule(getApplication())
            } else {
                // Stop P2P host
                p2pHostManager.stop()
                // Cancel periodic work
                P2PWorker.cancel(getApplication())
            }
        }
    }
    
    fun setBandwidthLimit(enabled: Boolean) {
        p2pPreferences.bandwidthLimitEnabled().set(enabled)
        _state.update { it.copy(hasBandwidthLimit = enabled) }
    }
    
    fun setWifiOnly(wifiOnly: Boolean) {
        p2pPreferences.wifiOnly().set(wifiOnly)
        _state.update { it.copy(wifiOnly = wifiOnly) }
    }
    
    fun setAutoAnnounce(enabled: Boolean) {
        p2pPreferences.autoAnnounce().set(enabled)
        _state.update { it.copy(autoAnnounce = enabled) }
    }
    
    fun setAutoDiscover(enabled: Boolean) {
        p2pPreferences.autoDiscover().set(enabled)
        _state.update { it.copy(autoDiscover = enabled) }
    }
    
    fun runManualSync() {
        viewModelScope.launch {
            P2PWorker.runOnce(getApplication())
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
