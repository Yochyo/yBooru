package de.yochyo.yummybooru.utils.network

import android.content.Context
import de.yochyo.downloader.RegulatingDownloader
import de.yochyo.yummybooru.api.entities.Resource
import de.yochyo.yummybooru.utils.general.Logger
import de.yochyo.yummybooru.utils.general.cache
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.InputStream

class CacheableDownloader(maxThreads: Int) {
    val dl = object : RegulatingDownloader<Resource>(maxThreads) {
        override fun toResource(inputStream: InputStream, context: Any): Resource {
            return Resource(inputStream.readBytes(), context as String)
        }
    }

    fun download(context: Context, url: String, id: String, callback: suspend (e: Resource) -> Unit, downloadFirst: Boolean = false, cacheFile: Boolean = false) {
        suspend fun doAfter(res: Resource?) {
            if (res != null) {
                if (cacheFile) GlobalScope.launch { context.cache.cacheFile(id, res) }
                callback(res)
            }
        }
        try {
            GlobalScope.launch {
                val res = context.cache.getCachedFile(id)
                when {
                    res != null -> doAfter(res)
                    downloadFirst -> dl.downloadNow(url, { doAfter(it) }, Resource.getMimetypeFromURL(url))
                    else -> dl.download(url, { doAfter(it) }, Resource.getMimetypeFromURL(url))
                }
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            Logger.log(e, filePrefix = "OutOfMemory")
        }
    }
}

val downloader = CacheableDownloader(5)
fun download(context: Context, url: String, id: String, callback: suspend (e: Resource) -> Unit, downloadFirst: Boolean = false, cacheFile: Boolean = false) = downloader.download(context, url, id, callback, downloadFirst, cacheFile)
