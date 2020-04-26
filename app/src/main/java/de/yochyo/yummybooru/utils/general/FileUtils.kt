package de.yochyo.yummybooru.utils.general

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import de.yochyo.booruapi.objects.Post
import de.yochyo.yummybooru.api.entities.Resource
import de.yochyo.yummybooru.api.entities.Server
import de.yochyo.yummybooru.database.db
import de.yochyo.yummybooru.events.events.SafeFileEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileUtils {

    suspend fun writeFile(context: Context, post: Post, res: Resource, server: Server, source: Int = SafeFileEvent.DEFAULT) {
        withContext(Dispatchers.IO) {
            val file = createFileToWrite(context, post, server, res.mimetype)
            if (file != null) {
                try {
                    context.contentResolver.openOutputStream(file.uri)!!.write(res.resource)
                    withContext(Dispatchers.Main) { SafeFileEvent.trigger(SafeFileEvent(context, file, post, source)) }
                } catch (e: Exception) {
                    Logger.log(e)
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun createFileToWrite(context: Context, post: Post, server: Server, mimeType: String): DocumentFile? {
        return withContext(Dispatchers.IO) {
            val folder = getOrCreateFolder(context.db.saveFolder, server.urlHost)
            if (folder != null) createFileOrNull(folder, postToFilename(post, mimeType, server), mimeType) else null
        }

    }

    private suspend fun postToFilename(p: Post, mimeType: String, server: Server): String {
        val s = "${server.urlHost} ${p.id} ${p.tagString}".filter { it != '/' && it != '\\' && it != '|' && it != ':' && it != '*' && it != '?' && it != '"' && it != '[' && it != ']' }
        var last = s.length
        if (last > 127 - (mimeType.length + 1)) last = 127 - (mimeType.length + 1)
        return s.substring(0, last) + ".$mimeType"
    }

    private fun createFileOrNull(parent: DocumentFile, name: String, mimeType: String): DocumentFile? {
        val file = parent.findFile(name)
        return if (file != null) null
        else return parent.createFile(mimeType, name)
    }

    private fun getOrCreateFolder(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name) ?: parent.createDirectory(name)
    }
}