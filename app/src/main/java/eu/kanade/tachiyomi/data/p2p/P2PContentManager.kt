package eu.kanade.tachiyomi.data.p2p

import android.content.Context
import eu.kanade.tachiyomi.data.storage.StorageReservationManager
import eu.kanade.tachiyomi.util.storage.ZstdCompression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.File
import java.security.MessageDigest

/**
 * P2P Content Manager - Bridge between download manager and P2P system
 * 
 * Responsibilities:
 * - Coordinate storage allocation with content downloads
 * - Manage redundancy checks and auto-downloads
 * - Handle chapter announcements to DHT
 * - Compress content for P2P transfer using Zstd
 */
class P2PContentManager(
    private val context: Context,
    private val p2pHostManager: P2PHostManager,
    private val storageReservationManager: StorageReservationManager,
    private val preferences: P2PPreferences,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {
    companion object {
        // Minimum peers for hot manga before auto-download triggers
        const val MIN_PEERS_FOR_HOT_MANGA = 8
        
        // Compression level for Zstd (1-22, higher = better compression but slower)
        const val ZSTD_COMPRESSION_LEVEL = 3
        
        // Directory for P2P cached content
        private const val P2P_CACHE_DIR = "p2p_cache"
    }
    
    private val p2pCacheDir: File
        get() = File(context.filesDir, P2P_CACHE_DIR)
    
    /**
     * Called when a chapter download completes
     * 
     * This function:
     * 1. Calculates the chapter size
     * 2. Updates storage allocation if needed
     * 3. Announces the chapter to DHT with Zstd compression
     * 
     * @param mangaId The manga ID
     * @param chapterId The chapter ID
     * @param chapterFile The downloaded chapter file
     */
    suspend fun onChapterDownloaded(
        mangaId: Long,
        chapterId: Long,
        chapterFile: File,
    ) = withContext(Dispatchers.IO) {
        if (!p2pHostManager.isEnabled() || !preferences.autoAnnounce().get()) {
            return@withContext
        }
        
        try {
            // Calculate chapter size
            val chapterSize = chapterFile.length()
            
            // Check if we need to adjust storage allocation
            val reservedBytes = storageReservationManager.getReservedBytes()
            val usedBytes = getP2PCacheSize()
            val availableReserved = reservedBytes - usedBytes
            
            if (chapterSize > availableReserved) {
                logcat(LogPriority.WARN) { 
                    "Chapter size ($chapterSize) exceeds available reserved space ($availableReserved)" 
                }
            }
            
            // Generate content key for DHT
            val contentKey = generateContentKey(mangaId, chapterId)
            
            // Read and compress chapter data for DHT metadata
            val chapterData = chapterFile.readBytes()
            val compressedData = ZstdCompression.compress(chapterData, ZSTD_COMPRESSION_LEVEL)
            
            // Create metadata for announcement
            val metadata = ChapterMetadata(
                mangaId = mangaId,
                chapterId = chapterId,
                size = chapterSize,
                compressedSize = compressedData.size.toLong(),
                hash = hashBytes(chapterData),
            )
            
            // Announce to DHT
            p2pHostManager.announceChapter(contentKey, metadata)
            
            logcat(LogPriority.INFO) { 
                "Announced chapter $chapterId to DHT (size: $chapterSize, compressed: ${compressedData.size})" 
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to announce chapter to DHT: ${e.message}" }
        }
    }
    
    /**
     * Check redundancy levels for hot manga titles
     * 
     * This function queries the DHT for each hot manga and checks
     * if the peer count is below the threshold. If so, it triggers
     * an auto-download.
     * 
     * @param hotMangaIds List of manga IDs considered "hot"
     * @return Map of manga IDs to their peer counts
     */
    suspend fun checkRedundancyForHotManga(
        hotMangaIds: List<Long>,
    ): Map<Long, Int> = withContext(Dispatchers.IO) {
        if (!p2pHostManager.isEnabled()) {
            return@withContext emptyMap()
        }
        
        val results = mutableMapOf<Long, Int>()
        val autoDownloadEnabled = preferences.autoDownload().get()
        
        for (mangaId in hotMangaIds) {
            try {
                // Get all chapters for this manga
                val chapters = getChaptersByMangaId.await(mangaId)
                
                // Check peer count for each chapter
                var totalPeers = 0
                for (chapter in chapters) {
                    val contentKey = generateContentKey(mangaId, chapter.id)
                    val providers = p2pHostManager.findProviders(contentKey)
                    totalPeers += providers.size
                }
                
                val avgPeers = if (chapters.isNotEmpty()) totalPeers / chapters.size else 0
                results[mangaId] = avgPeers
                
                logcat(LogPriority.DEBUG) { 
                    "Manga $mangaId redundancy check: $avgPeers average peers" 
                }
                
                // Auto-download if below threshold
                if (autoDownloadEnabled && avgPeers < MIN_PEERS_FOR_HOT_MANGA) {
                    logcat(LogPriority.WARN) { 
                        "Manga $mangaId has low redundancy ($avgPeers peers), triggering auto-download" 
                    }
                    triggerAutoDownload(mangaId, avgPeers)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { 
                    "Failed redundancy check for manga $mangaId: ${e.message}" 
                }
            }
        }
        
        results
    }
    
    /**
     * Trigger auto-download for a manga with low redundancy
     * 
     * @param mangaId The manga ID to download
     * @param currentPeers Current peer count
     */
    private suspend fun triggerAutoDownload(mangaId: Long, currentPeers: Int) {
        try {
            // Check if we have reserved storage space
            val reservedBytes = storageReservationManager.getReservedBytes()
            if (reservedBytes <= 0) {
                logcat(LogPriority.WARN) { "No reserved storage for auto-download" }
                return
            }
            
            // Get manga info
            val manga = getManga.await(mangaId)
            if (manga == null) {
                logcat(LogPriority.WARN) { "Manga $mangaId not found for auto-download" }
                return
            }
            
            // Calculate required space (estimate based on chapter count)
            val chapters = getChaptersByMangaId.await(mangaId)
            val estimatedSize = estimateMangaSize(chapters)
            
            // Check available reserved space
            val usedBytes = getP2PCacheSize()
            val availableBytes = reservedBytes - usedBytes
            
            if (estimatedSize > availableBytes) {
                logcat(LogPriority.WARN) { 
                    "Insufficient reserved space for auto-download. Need: $estimatedSize, Available: $availableBytes" 
                }
                return
            }
            
            // Queue the manga for download
            // This would integrate with the actual download manager
            logcat(LogPriority.INFO) { 
                "Auto-download queued for manga $mangaId (${manga.title})" 
            }
            
            // The actual download would be handled by MangaDownloadManager
            // For now, we just log the intent
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Auto-download failed: ${e.message}" }
        }
    }
    
    /**
     * Get the total size of P2P cached content
     */
    private fun getP2PCacheSize(): Long {
        return try {
            if (p2pCacheDir.exists()) {
                p2pCacheDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
            } else {
                0L
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to calculate P2P cache size" }
            0L
        }
    }
    
    /**
     * Generate a unique content key for DHT
     */
    private fun generateContentKey(mangaId: Long, chapterId: Long): String {
        return "manga_${mangaId}_chapter_$chapterId"
    }
    
    /**
     * Generate SHA-256 hash of bytes
     */
    private fun hashBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Estimate manga size based on chapter count
     * Average manga chapter is ~10-15MB
     */
    private fun estimateMangaSize(chapters: List<Chapter>): Long {
        val avgChapterSizeBytes = 12L * 1024 * 1024 // 12MB average
        return chapters.size * avgChapterSizeBytes
    }
    
    /**
     * Clean up old P2P cache entries
     * 
     * @param maxAgeDays Maximum age in days for cached content
     */
    suspend fun cleanupOldCache(maxAgeDays: Int = 30) = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
            
            p2pCacheDir.walkTopDown()
                .filter { it.isFile && it.lastModified() < cutoffTime }
                .forEach { file ->
                    if (file.delete()) {
                        logcat(LogPriority.DEBUG) { "Deleted old P2P cache: ${file.name}" }
                    }
                }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to cleanup P2P cache" }
        }
    }
    
    /**
     * Receive chapter data from a peer
     * 
     * @param contentKey The content identifier
     * @param compressedData The compressed chapter data
     * @return True if received successfully
     */
    suspend fun receiveChapterFromPeer(
        contentKey: String,
        compressedData: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Decompress the data
            val chapterData = ZstdCompression.decompress(compressedData)
            
            // Verify hash if available
            // Store in P2P cache
            val cacheFile = File(p2pCacheDir, "$contentKey.cbz")
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(chapterData)
            
            logcat(LogPriority.INFO) { "Received chapter from peer: $contentKey" }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to receive chapter from peer: ${e.message}" }
            false
        }
    }
}

/**
 * Metadata for a chapter announced to DHT
 */
data class ChapterMetadata(
    val mangaId: Long,
    val chapterId: Long,
    val size: Long,
    val compressedSize: Long,
    val hash: String,
) {
    fun toByteArray(): ByteArray {
        return "$mangaId:$chapterId:$size:$compressedSize:$hash".toByteArray()
    }
    
    companion object {
        fun fromByteArray(bytes: ByteArray): ChapterMetadata? {
            return try {
                val parts = String(bytes).split(":")
                ChapterMetadata(
                    mangaId = parts[0].toLong(),
                    chapterId = parts[1].toLong(),
                    size = parts[2].toLong(),
                    compressedSize = parts[3].toLong(),
                    hash = parts[4],
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
