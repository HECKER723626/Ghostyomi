# P2P Storage Integration Plan

## Overview
This document outlines the integration between Storage Reservation and P2P Discovery systems for the Ghostyomi fork.

## Architecture Components

### 1. P2PContentManager (New)
Location: `app/src/main/java/eu/kanade/tachiyomi/data/p2p/P2PContentManager.kt`

**Responsibilities:**
- Bridge between download manager and P2P system
- Coordinate storage allocation with content downloads
- Manage redundancy checks and auto-downloads

**Key Functions:**
```kotlin
class P2PContentManager(
    private val p2pHostManager: P2PHostManager,
    private val storageReservationManager: StorageReservationManager,
    private val downloadManager: MangaDownloadManager,
) {
    // Called when chapter download completes
    suspend fun onChapterDownloaded(mangaId: Long, chapterId: Long)
    
    // Check redundancy for hot manga
    suspend fun checkRedundancyForHotManga()
    
    // Auto-download if peers < threshold
    suspend fun autoDownloadIfNeeded(mangaId: Long, peerCount: Int)
}
```

### 2. Modified P2PWorker

**Changes Required:**
1. Add `P2PContentManager` injection
2. Implement `checkRedundancy()` with actual DHT queries
3. Implement `announceLocalContent()` with actual chapter announcements
4. Add storage allocation on download completion

### 3. Redundancy Check Flow

```
┌─────────────────────────────────────────────────────────────┐
│                   P2PWorker (Every 6 Hours)                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Check Reserved Storage Exists                   │
│              (storageReservationManager.getReservedBytes)    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Query DHT for Hot Manga Titles                  │
│              (p2pHostManager.findProviders)                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              For Each Hot Manga:                             │
│              - Count available peers                         │
│              - If peers < 8: trigger auto-download           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Auto-Download Flow:                             │
│              1. Check reserved storage space                 │
│              2. Download with Zstd compression               │
│              3. Store in reserved location                   │
│              4. Announce to DHT                              │
└─────────────────────────────────────────────────────────────┘
```

### 4. Download Completion Flow

```
┌─────────────────────────────────────────────────────────────┐
│              Chapter Download Completes                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              P2PContentManager.onChapterDownloaded           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              1. Calculate chapter size                       │
│              2. Update storage allocation                    │
│              3. Announce to DHT with Zstd compression        │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Details

### Hot Manga Definition
A manga is considered "Hot" if:
- It has > 1000 reads across the network
- It was updated within the last 7 days
- It appears in the top 100 trending titles

### Redundancy Thresholds
- **Minimum Peers**: 8 (for hot manga)
- **Warning Level**: 5-7 peers
- **Critical Level**: 3-4 peers
- **Auto-Download Trigger**: < 3 peers

### Storage Allocation Strategy
1. **Pre-allocation**: Reserve space at app start based on preferences
2. **Dynamic Adjustment**: Increase allocation when auto-downloading hot content
3. **Cleanup**: Release space when content is removed

### Zstd Compression Integration
- Compress chapter data before announcing to DHT
- Decompress when receiving from peers
- Compression level: 3 (balanced speed/ratio)

## Files to Modify

1. **P2PWorker.kt** - Add redundancy check logic and storage allocation
2. **P2PContentManager.kt** (new) - Bridge component
3. **MangaDownloadManager.kt** - Add callback for download completion
4. **StorageReservationManager.kt** - Add methods for dynamic allocation

## Configuration

Add to `P2PPreferences`:
```kotlin
// Redundancy check interval in hours
fun redundancyCheckInterval() = preferences.getInt("p2p_redundancy_interval", 6)

// Minimum peers for hot manga
fun minPeersForHotManga() = preferences.getInt("p2p_min_peers_hot", 8)

// Enable auto-download for low redundancy
fun autoDownloadEnabled() = preferences.getBoolean("p2p_auto_download", true)
```

## Testing Strategy

1. **Unit Tests**: Test redundancy logic with mock DHT
2. **Integration Tests**: Test storage allocation with downloads
3. **Manual Testing**: Verify DHT announcements appear correctly

## Next Steps

1. Create `P2PContentManager.kt`
2. Modify `P2PWorker.kt` with actual implementation
3. Add download completion callback to `MangaDownloadManager`
4. Test build with `./gradlew assembleDebug`
