package eu.kanade.tachiyomi.data.p2p

import android.content.Context
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Preferences for P2P functionality
 */
class P2PPreferences(
    private val preferenceStore: PreferenceStore,
) {
    
    companion object {
        private const val PREF_P2P_ENABLED = "pref_p2p_enabled"
        private const val PREF_PRIVATE_KEY = "p2p_private_key"
        private const val PREF_PEER_ID = "p2p_peer_id"
        private const val PREF_AUTO_ANNOUNCE = "p2p_auto_announce"
        private const val PREF_AUTO_DISCOVER = "p2p_auto_discover"
        private const val PREF_REDUNDANCY_CHECK_INTERVAL = "p2p_redundancy_check_interval"
        private const val PREF_MIN_PEERS_HOT = "p2p_min_peers_hot"
        private const val PREF_AUTO_DOWNLOAD = "p2p_auto_download"
        private const val PREF_WIFI_ONLY = "p2p_wifi_only"
        private const val PREF_BANDWIDTH_LIMIT = "p2p_bandwidth_limit"
        private const val PREF_TOTAL_UPLOADED = "p2p_total_uploaded"
        private const val PREF_TOTAL_DOWNLOADED = "p2p_total_downloaded"
    }
    
    fun p2pEnabled(): Preference<Boolean> {
        return preferenceStore.getBoolean(PREF_P2P_ENABLED, false)
    }
    
    fun privateKey(): Preference<String> {
        return preferenceStore.getString(PREF_PRIVATE_KEY, "")
    }
    
    fun peerId(): Preference<String> {
        return preferenceStore.getString(PREF_PEER_ID, "")
    }
    
    fun autoAnnounce(): Preference<Boolean> {
        return preferenceStore.getBoolean(PREF_AUTO_ANNOUNCE, true)
    }
    
    fun autoDiscover(): Preference<Boolean> {
        return preferenceStore.getBoolean(PREF_AUTO_DISCOVER, true)
    }
    
    fun redundancyCheckInterval(): Preference<Int> {
        return preferenceStore.getInt(PREF_REDUNDANCY_CHECK_INTERVAL, 6)
    }
    
    fun minPeersForHotManga(): Preference<Int> {
        return preferenceStore.getInt(PREF_MIN_PEERS_HOT, 8)
    }
    
    fun autoDownload(): Preference<Boolean> {
        return preferenceStore.getBoolean(PREF_AUTO_DOWNLOAD, true)
    }
    
    fun wifiOnly(): Preference<Boolean> {
        return preferenceStore.getBoolean(PREF_WIFI_ONLY, true)
    }
    
    fun bandwidthLimitEnabled(): Preference<Boolean> {
        return preferenceStore.getBoolean(PREF_BANDWIDTH_LIMIT, false)
    }
    
    fun totalUploaded(): Preference<Long> {
        return preferenceStore.getLong(PREF_TOTAL_UPLOADED, 0L)
    }
    
    fun totalDownloaded(): Preference<Long> {
        return preferenceStore.getLong(PREF_TOTAL_DOWNLOADED, 0L)
    }
}
