package com.demonlab.lune.tools

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse

class AudioThumbnailFetcher(
    private val uri: Uri,
    private val options: Options,
    private val context: Context
) : Fetcher {

    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun fetch(): FetchResult? {
        // 1. Try modern ContentResolver API (Guaranteed Android Q+ by Factory)
        val bitmap = runCatching {
            val width = options.size.width.pxOrElse { 512 }
            val height = options.size.height.pxOrElse { 512 }
            context.contentResolver.loadThumbnail(uri, android.util.Size(width, height), null)
        }.getOrNull()

        if (bitmap != null) {
            return DrawableResult(
                drawable = bitmap.toDrawable(context.resources),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }

        // 2. Fallback to MediaMetadataRetriever if loadThumbnail fails
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { picture ->
                val fallbackBitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                DrawableResult(
                    drawable = fallbackBitmap.toDrawable(context.resources),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only intercept generic media URIs for audio on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                data.scheme == "content" &&
                data.toString().contains("audio/media")) {
                return AudioThumbnailFetcher(data, options, context)
            }
            return null
        }
    }
}