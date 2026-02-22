package eu.kanade.tachiyomi.util.storage

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Zstd compression utility for P2P metadata
 * 
 * Zstd provides excellent compression ratios (~26% better than PNG for images)
 * while maintaining fast compression/decompression speeds.
 */
object ZstdCompression {
    
    /**
     * Compress data using Zstd
     * @param data The data to compress
     * @return Compressed data
     */
    fun compress(data: ByteArray): ByteArray {
        return try {
            Zstd.compress(data)
        } catch (e: Exception) {
            // Return original data if compression fails
            data
        }
    }
    
    /**
     * Decompress Zstd compressed data
     * @param compressedData The compressed data
     * @return Decompressed data
     */
    fun decompress(compressedData: ByteArray): ByteArray {
        return try {
            val decompressedSize = Zstd.decompressedSize(compressedData)
            if (decompressedSize > 0) {
                Zstd.decompress(compressedData, decompressedSize.toInt())
            } else {
                // If size is unknown, try with larger buffer
                Zstd.decompress(compressedData, compressedData.size * 10)
            }
        } catch (e: Exception) {
            // Return original data if decompression fails
            compressedData
        }
    }
    
    /**
     * Compress data with streaming (better for large data)
     * @param data The data to compress
     * @return Compressed data
     */
    fun compressStream(data: ByteArray): ByteArray {
        return try {
            val outputStream = ByteArrayOutputStream()
            ZstdOutputStream(outputStream).use { zstdOut ->
                zstdOut.write(data)
            }
            outputStream.toByteArray()
        } catch (e: Exception) {
            data
        }
    }
    
    /**
     * Decompress data with streaming
     * @param compressedData The compressed data
     * @return Decompressed data
     */
    fun decompressStream(compressedData: ByteArray): ByteArray {
        return try {
            val inputStream = ByteArrayInputStream(compressedData)
            ZstdInputStream(inputStream).use { zstdIn ->
                zstdIn.readBytes()
            }
        } catch (e: Exception) {
            compressedData
        }
    }
    
    /**
     * Get the original size from compressed data (if available)
     * @param compressedData The compressed data
     * @return Original size or -1 if unknown
     */
    fun getOriginalSize(compressedData: ByteArray): Long {
        return try {
            Zstd.decompressedSize(compressedData)
        } catch (e: Exception) {
            -1L
        }
    }
    
    /**
     * Get compression ratio
     * @param original Original data size
     * @param compressed Compressed data size
     * @return Compression ratio as percentage (e.g., 74.0 for 74%)
     */
    fun getCompressionRatio(original: Int, compressed: Int): Double {
        return if (original > 0) {
            (compressed.toDouble() / original.toDouble()) * 100.0
        } else {
            100.0
        }
    }
}
