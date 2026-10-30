package com.aisendiri.shared.android

import android.content.Context
import android.os.StatFs
import com.aisendiri.shared.model.ModelFile
import com.aisendiri.shared.platform.DownloadError
import com.aisendiri.shared.platform.DownloadState
import com.aisendiri.shared.platform.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * Stores AI files in the app's private storage. A download is written to a ".part" file,
 * so it can resume after a pause or dropped connection, and is only renamed to its final
 * name after its size and SHA-256 match what Hugging Face publishes.
 */
class AndroidModelRepository(context: Context) : ModelRepository {
    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    private fun finalFile(model: ModelFile) = File(dir, model.fileName)
    private fun partFile(model: ModelFile) = File(dir, model.fileName + ".part")

    override fun isInstalled(model: ModelFile): Boolean = finalFile(model).let { it.isFile && it.length() > 0 }
    override fun filePath(model: ModelFile): String = finalFile(model).absolutePath
    override fun partialBytes(model: ModelFile): Long = partFile(model).length()

    override suspend fun delete(model: ModelFile) {
        withContext(Dispatchers.IO) {
            finalFile(model).delete()
            partFile(model).delete()
        }
    }

    override fun download(model: ModelFile): Flow<DownloadState> = flow {
        emit(DownloadState.Preparing)
        if (isInstalled(model)) {
            emit(DownloadState.Done)
            return@flow
        }
        val part = partFile(model)
        try {
            val remote = fetchRemoteInfo(model)
            val total = remote?.size ?: model.approxSizeBytes
            val needed = total - part.length() + SPACE_MARGIN_BYTES
            if (StatFs(dir.path).availableBytes < needed) {
                emit(DownloadState.Failed(DownloadError.NOT_ENOUGH_STORAGE))
                return@flow
            }

            val finalTotal = transfer(model, part, remote?.size)

            emit(DownloadState.Verifying)
            val problem = verify(part, finalTotal, model.magic, remote?.sha256)
            if (problem != null) {
                part.delete()
                emit(DownloadState.Failed(DownloadError.CORRUPT, problem))
                return@flow
            }
            if (!part.renameTo(finalFile(model))) throw IOException("Could not save the AI file")
            emit(DownloadState.Done)
        } catch (e: UnknownHostException) {
            emit(DownloadState.Failed(DownloadError.NO_INTERNET, e.message))
        } catch (e: java.net.SocketException) {
            emit(DownloadState.Failed(DownloadError.NO_INTERNET, e.message))
        } catch (e: java.net.SocketTimeoutException) {
            emit(DownloadState.Failed(DownloadError.NO_INTERNET, e.message))
        } catch (e: ServerException) {
            emit(DownloadState.Failed(DownloadError.SERVER, e.message))
        } catch (e: IOException) {
            val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true
            emit(DownloadState.Failed(if (noSpace) DownloadError.NOT_ENOUGH_STORAGE else DownloadError.UNKNOWN, e.message))
        }
    }.flowOn(Dispatchers.IO)

    /** Downloads into [part], resuming from its current length. Returns the full file size. */
    private suspend fun FlowCollector<DownloadState>.transfer(model: ModelFile, part: File, knownSize: Long?): Long {
        var existing = part.length()
        if (knownSize != null && existing == knownSize) return knownSize

        val conn = open(model.downloadUrl).apply {
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false.also { existing = 0 } // server ignored the range: start over
                416 -> return existing // range not satisfiable: the part file is already complete
                else -> throw ServerException("HTTP $code")
            }
            val total = knownSize ?: (existing + conn.contentLengthLong.coerceAtLeast(0))
            var done = existing
            var lastEmit = 0L
            var speedBytes = 0L
            var speedStart = System.nanoTime()
            var bytesPerSecond = 0L
            emit(DownloadState.Downloading(done, total, 0))

            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive() // pausing cancels here; the part file is kept
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        speedBytes += n
                        val now = System.nanoTime()
                        if (now - speedStart >= 1_000_000_000L) {
                            bytesPerSecond = speedBytes * 1_000_000_000L / (now - speedStart)
                            speedBytes = 0
                            speedStart = now
                        }
                        if (now - lastEmit >= EMIT_INTERVAL_NS) {
                            lastEmit = now
                            emit(DownloadState.Downloading(done, total, bytesPerSecond))
                        }
                    }
                }
            }
            emit(DownloadState.Downloading(done, total, bytesPerSecond))
            return total
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun verify(file: File, expectedSize: Long, magicText: String, sha256: String?): String? {
        if (file.length() != expectedSize) return "size ${file.length()} != $expectedSize"
        val magic = ByteArray(4)
        file.inputStream().use { if (it.read(magic) != 4) return "file too small" }
        if (String(magic, Charsets.US_ASCII) != magicText) return "unexpected file type"
        if (sha256 == null) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (actual.equals(sha256, ignoreCase = true)) null else "checksum mismatch"
    }

    private data class RemoteInfo(val size: Long, val sha256: String?)

    /** Asks Hugging Face for the file's exact size and SHA-256. Returns null if it isn't listed. */
    private fun fetchRemoteInfo(model: ModelFile): RemoteInfo? {
        val conn = open("https://huggingface.co/api/models/${model.hfRepo}/tree/main")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw ServerException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val entry = json.parseToJsonElement(body).jsonArray
                .map { it.jsonObject }
                .firstOrNull { it["path"]?.jsonPrimitive?.content == model.fileName } ?: return null
            val lfs = entry["lfs"] as? JsonObject
            val size = lfs?.get("size")?.jsonPrimitive?.longOrNull ?: entry["size"]?.jsonPrimitive?.longOrNull ?: return null
            val sha = lfs?.get("oid")?.jsonPrimitive?.content?.takeIf { it.length == 64 }
            return RemoteInfo(size, sha)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AiSendiri-Android")
        }

    private class ServerException(message: String) : IOException(message)

    private companion object {
        const val BUFFER_BYTES = 256 * 1024
        const val EMIT_INTERVAL_NS = 300_000_000L
        const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024
        val json = Json { ignoreUnknownKeys = true }
    }
}
