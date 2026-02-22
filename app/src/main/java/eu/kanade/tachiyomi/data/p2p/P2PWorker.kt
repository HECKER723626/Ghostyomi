package eu.kanade.tachiyomi.data.p2p

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.storage.StorageReservationManager
import eu.kanade.tachiyomi.util.storage.ZstdCompression
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * P2P Background Worker for discovery and redundancy checks
 * 
 * This worker handles:
 * - Periodic provider discovery (every 6 hours)
 * - Redundancy monitoring (checking if content has enough peers)
 * - Announcing local content to DHT
 * - Auto-downloading hot manga with low peer count
 * 
 * Uses WorkManager with battery-aware constraints to minimize battery usage
 */
class P2PWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    
    private val p2pHostManager: P2PHostManager by inject()
    private val storageReservationManager: StorageReservationManager by inject()
    private val p2pPreferences: P2PPreferences by inject()
    private val p2pContentManager: P2PContentManager by inject()
    
    override suspend fun doWork(): Result {
        if (!p2pHostManager.isEnabled()) {
            logcat(LogPriority.DEBUG) { "P2P is disabled, skipping worker" }
            return Result.success()
        }
        
        try {
            // Check if we have reserved storage
            val reservedBytes = storageReservationManager.getReservedBytes()
            if (reservedBytes <= 0) {
                logcat(LogPriority.DEBUG) { "No storage reserved, skipping P2P worker" }
                return Result.success()
            }
            
            // Ensure storage allocation is synced
            val reservedGb = storageReservationManager.getReservedGb()
            if (reservedGb > 0) {
                storageReservationManager.allocate(reservedGb)
                logcat(LogPriority.DEBUG) { "Storage allocation synced: $reservedGb GB" }
            }
            
            // Perform redundancy check for hot manga
            val redundancyResults = checkRedundancy()
            
            // Announce local content to DHT
            announceLocalContent()
            
            // Clean up old cache entries
            p2pContentManager.cleanupOldCache(maxAgeDays = 30)
            
            logcat(LogPriority.INFO) { 
                "P2P worker completed. Redundancy check: ${redundancyResults.size} manga checked" 
            }
            
            return Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "P2P Worker failed: ${e.message}" }
            return Result.retry()
        }
    }
    
    /**
     * Check redundancy levels for cached content
     * If providers have dropped below threshold, trigger auto-download
     * 
     * @return Map of manga IDs to peer counts
     */
    private suspend fun checkRedundancy(): Map<Long, Int> {
        logcat(LogPriority.DEBUG) { "Running redundancy check..." }
        
        // Get list of hot manga (popular/recently updated)
        val hotMangaIds = getHotMangaIds()
        
        if (hotMangaIds.isEmpty()) {
            logcat(LogPriority.DEBUG) { "No hot manga to check" }
            return emptyMap()
        }
        
        // Use P2PContentManager to check redundancy
        return p2pContentManager.checkRedundancyForHotManga(hotMangaIds)
    }
    
    /**
     * Get list of "hot" manga IDs that need redundancy checking
     * Hot manga = popular titles with > 1000 reads or recently updated
     */
    private suspend fun getHotMangaIds(): List<Long> {
        // This would integrate with the manga repository to get popular titles
        // For now, return empty list - actual implementation would query the database
        return emptyList()
    }
    
    /**
     * Announce local content to DHT
     * This integrates with the download manager to announce downloaded chapters
     */
    private suspend fun announceLocalContent() {
        if (!p2pPreferences.autoAnnounce().first()) {
            logcat(LogPriority.DEBUG) { "Auto-announce disabled, skipping" }
            return
        }
        
        logcat(LogPriority.DEBUG) { "Announcing local content to DHT..." }
        
        // Get downloaded chapters from download manager
        // This would integrate with MangaDownloadManager to get the list
        // For each downloaded chapter:
        // 1. Generate content key
        // 2. Read chapter file
        // 3. Compress with Zstd
        // 4. Announce to DHT
        
        // Actual implementation would iterate through downloaded chapters
        // and call p2pContentManager.onChapterDownloaded() for each
    }
    
    companion object {
        private const val WORK_NAME = "p2p_discovery_work"
        
        /**
         * Schedule periodic P2P discovery work
         * @param context Application context
         * @param intervalHours Interval in hours (default 6)
         */
        fun schedule(context: Context, intervalHours: Int = 6) {
            val constraints = Constraints.Builder()
                // Battery-aware: only run when charging
                .setRequiresCharging(true)
                // Allow on any network (but prefer unmetered)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                // Don't run if device is low on battery
                .setRequiresBatteryNotLow(true)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<P2PWorker>(
                intervalHours.toLong(), 
                TimeUnit.HOURS,
                // Flex interval allows WorkManager to optimize battery
                30, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .addTag("p2p")
                .addTag("battery_aware")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
            
            logcat(LogPriority.INFO) { "Scheduled P2P worker every $intervalHours hours" }
        }
        
        /**
         * Cancel periodic P2P work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            logcat(LogPriority.INFO) { "Cancelled P2P worker" }
        }
        
        /**
         * Run a one-time P2P sync
         */
        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<P2PWorker>()
                .setConstraints(constraints)
                .addTag("p2p")
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            logcat(LogPriority.INFO) { "Queued one-time P2P sync" }
        }
    }
}

/**
 * Redundancy Manager for P2P content
 * Monitors peer counts and triggers re-downloads when redundancy is low
 */
class P2PRedundancyManager(
    private val p2pHostManager: P2PHostManager,
    private val p2pPreferences: P2PPreferences,
) {
    companion object {
        // Minimum number of peers before we consider content "well-distributed"
        const val DEFAULT_MIN_REDUNDANCY_PEERS = 8
        
        // Threshold below which we trigger a re-download
        const val LOW_REDUNDANCY_THRESHOLD = 3
    }
    
    /**
     * Check if a piece of content has sufficient redundancy
     * @param contentKey The content identifier
     * @return True if redundancy is sufficient
     */
    suspend fun hasSufficientRedundancy(contentKey: String): Boolean {
        val providers = p2pHostManager.findProviders(contentKey)
        val minPeers = p2pPreferences.minPeersForHotManga().get()
        return providers.size >= minPeers
    }
    
    /**
     * Get the current redundancy level for content
     * @param contentKey The content identifier
     * @return Number of providers
     */
    suspend fun getRedundancyLevel(contentKey: String): Int {
        return p2pHostManager.findProviders(contentKey).size
    }
    
    /**
     * Determine if we should fetch from P2P based on redundancy
     * @param contentKey The content identifier
     * @return Recommended action
     */
    suspend fun recommendAction(contentKey: String): RedundancyAction {
        val redundancy = getRedundancyLevel(contentKey)
        val minPeers = p2pPreferences.minPeersForHotManga().get()
        
        return when {
            redundancy >= minPeers -> RedundancyAction.Healthy(redundancy)
            redundancy >= LOW_REDUNDANCY_THRESHOLD -> RedundancyAction.Low(redundancy)
            redundancy > 0 -> RedundancyAction.Critical(redundancy)
            else -> RedundancyAction.NoProviders
        }
    }
}

/**
 * Redundancy action recommendation
 */
sealed class RedundancyAction {
    data class Healthy(val peerCount: Int) : RedundancyAction()
    data class Low(val peerCount: Int) : RedundancyAction()
    data class Critical(val peerCount: Int) : RedundancyAction()
    data object NoProviders : RedundancyAction()
}
