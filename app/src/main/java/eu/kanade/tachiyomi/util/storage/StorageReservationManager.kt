package eu.kanade.tachiyomi.util.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import eu.kanade.tachiyomi.data.storage.StorageReservationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.RandomAccessFile

/**
 * Manager for storage reservation using sparse files.
 * This allows users to pre-allocate storage space that appears as "occupied" to the Android system,
 * making it available for P2P data storage without actually consuming disk space.
 *
 * Sparse files don't actually consume disk space but report as occupied to the OS,
 * allowing the reserved space to be used for P2P data while preventing the system
 * from filling up the storage.
 */
class StorageReservationManager(
    private val context: Context,
    private val preferences: StorageReservationPreferences = Injekt.get(),
) {

    companion object {
        private const val RESERVATION_FILE_NAME = ".p2p_storage_reserved"
        private const val GB_IN_BYTES = 1024L * 1024L * 1024L
        private const val MAX_RESERVATION_GB = 100
    }

    private val reservationFile: File
        get() = File(context.filesDir, RESERVATION_FILE_NAME)

    /**
     * Allocates or expands the sparse file to reserve the specified amount of storage.
     * @param gb The amount of storage to reserve in gigabytes
     * @return true if allocation was successful, false otherwise
     */
    suspend fun allocate(gb: Int): Boolean = withContext(Dispatchers.IO) {
        if (gb <= 0) {
            release()
            return@withContext true
        }

        if (gb > MAX_RESERVATION_GB) {
            logcat(LogPriority.ERROR) { "Storage reservation cannot exceed $MAX_RESERVATION_GB GB" }
            return@withContext false
        }

        try {
            val targetSize = gb.toLong() * GB_IN_BYTES
            val currentSize = reservationFile.length()

            if (currentSize == targetSize) {
                // Already at target size
                return@withContext true
            }

            // Use RandomAccessFile with "rw" mode to create sparse file
            RandomAccessFile(reservationFile, "rw").use { raf ->
                raf.setLength(targetSize)
            }

            logcat(LogPriority.INFO) { "Storage reservation allocated: $gb GB" }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to allocate storage reservation" }
            false
        }
    }

    /**
     * Releases the reserved storage by deleting the sparse file.
     * @return true if release was successful, false otherwise
     */
    suspend fun release(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (reservationFile.exists()) {
                val deleted = reservationFile.delete()
                if (deleted) {
                    logcat(LogPriority.INFO) { "Storage reservation released" }
                }
                deleted
            } else {
                true
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to release storage reservation" }
            false
        }
    }

    /**
     * Gets the currently reserved bytes from the sparse file.
     * @return The reserved bytes, or 0 if no reservation exists
     */
    fun getReservedBytes(): Long {
        return try {
            if (reservationFile.exists()) {
                reservationFile.length()
            } else {
                0L
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get reserved bytes" }
            0L
        }
    }

    /**
     * Gets the reserved storage in GB.
     * @return The reserved GB value from preferences (may differ from actual file size)
     */
    suspend fun getReservedGb(): Int {
        return preferences.reservedGb().first()
    }

    /**
     * Gets the actual free space on the device (total - reserved - system used).
     * @return The actual free bytes available for apps
     */
    fun getFreeBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBlocks = stat.availableBlocksLong
            val blockSize = stat.blockSizeLong
            availableBlocks * blockSize
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get free bytes" }
            0L
        }
    }

    /**
     * Gets the total storage capacity of the device.
     * @return The total bytes of the device storage
     */
    fun getTotalBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBlocks = stat.blockCountLong
            val blockSize = stat.blockSizeLong
            totalBlocks * blockSize
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get total bytes" }
            0L
        }
    }

    /**
     * Gets the "apparent" free space that apps see (total - reserved).
     * This is what the Android system reports as available after accounting for the sparse file.
     * @return The apparent free bytes
     */
    fun getApparentFreeBytes(): Long {
        val total = getTotalBytes()
        val reserved = getReservedBytes()
        return (total - reserved).coerceAtLeast(0)
    }

    /**
     * Gets the actual used space by the system (total - apparent free - reserved).
     * @return The bytes used by system and other data
     */
    fun getSystemUsedBytes(): Long {
        val total = getTotalBytes()
        val apparentFree = getApparentFreeBytes()
        val reserved = getReservedBytes()
        return (total - apparentFree - reserved).coerceAtLeast(0)
    }

    /**
     * Syncs the storage reservation with the preference value.
     * Call this on app startup to ensure the reservation matches the saved preference.
     */
    suspend fun syncWithPreferences(): Boolean {
        val reservedGb = preferences.reservedGb().first()
        return if (reservedGb > 0) {
            allocate(reservedGb)
        } else {
            release()
        }
    }
}
