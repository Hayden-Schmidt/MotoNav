package com.motonav.app.ride

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Phase C distribution (revised, see docs/DEV_LOG.md) — the NZ tile tarball is built once on a
// dev machine (Podman + Valhalla docker, never on a user's device) and hosted as a GitHub Release
// asset. This is a plain HTTPS GET into the path LocalTileFiles already expects; no adb, no
// Docker, no side-loading knowledge needed from the end user (BRouter's in-app "Download
// Manager" is the precedent). Single hardcoded URL for one region — revisit as a setting if/when
// multiple regions exist.
private const val NZ_TILES_URL =
    "https://github.com/Hayden-Schmidt/MotoNav/releases/download/nz-tiles-2026-09-18/nz_valhalla_tiles.tar"

sealed class TileDownloadProgress {
    data class InProgress(val bytesRead: Long, val totalBytes: Long) : TileDownloadProgress()
    data object Done : TileDownloadProgress()
    data class Failed(val message: String) : TileDownloadProgress()
}

object OfflineTileDownloader {
    private val client = OkHttpClient()

    /** Downloads to a `.part` file, then renames onto [LocalTileFiles.tileTarFile] — an
     * interrupted download never leaves a half-written file at the path Valhalla actually reads. */
    suspend fun downloadNzTiles(context: Context, onProgress: (TileDownloadProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir == null) {
                onProgress(TileDownloadProgress.Failed("No external files dir"))
                return@withContext
            }
            val destFile = LocalTileFiles.tileTarFile(externalFilesDir)
            destFile.parentFile?.mkdirs()
            val tmpFile = File(destFile.parentFile, "${destFile.name}.part")

            try {
                val request = Request.Builder().url(NZ_TILES_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onProgress(TileDownloadProgress.Failed("HTTP ${response.code}"))
                        return@withContext
                    }
                    val body = response.body
                    val totalBytes = body.contentLength()
                    var bytesRead = 0L
                    body.byteStream().use { input ->
                        tmpFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesRead += read
                                onProgress(TileDownloadProgress.InProgress(bytesRead, totalBytes))
                            }
                        }
                    }
                }
                if (!tmpFile.renameTo(destFile)) {
                    onProgress(TileDownloadProgress.Failed("Could not finalize downloaded file"))
                    return@withContext
                }
                onProgress(TileDownloadProgress.Done)
            } catch (e: IOException) {
                tmpFile.delete()
                onProgress(TileDownloadProgress.Failed(e.message ?: "Download failed"))
            }
        }
    }
}
