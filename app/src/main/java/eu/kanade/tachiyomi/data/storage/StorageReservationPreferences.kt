package eu.kanade.tachiyomi.data.storage

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Preferences for storage reservation functionality.
 * Allows users to pre-allocate storage using sparse files to reserve space for P2P data.
 */
class StorageReservationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        const val PREF_STORAGE_RESERVATION_GB = "pref_storage_reservation_gb"
    }

    /**
     * Returns the preference for storage reservation in GB.
     * 0 means disabled (no reservation).
     * Value range: 0-100 GB
     */
    fun reservedGb() = preferenceStore.getInt(PREF_STORAGE_RESERVATION_GB, 0)
}
