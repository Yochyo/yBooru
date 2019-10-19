package de.yochyo.yummybooru.api.downloads

import android.content.Context
import android.util.SparseArray
import de.yochyo.eventmanager.Event
import de.yochyo.eventmanager.EventCollection
import de.yochyo.eventmanager.EventHandler
import de.yochyo.yummybooru.api.Post
import de.yochyo.yummybooru.api.api.Api
import de.yochyo.yummybooru.utils.toTagArray
import de.yochyo.yummybooru.utils.toTagString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue

class Manager(val tags: Array<String>) {
    val tagString: String = tags.toTagString()

    companion object {
        var current: Manager = Manager("*".toTagArray())
    }

    val downloadManagerPageEvent = EventHandler<DownloadManagerPageEvent>()
    val loadManagerPageEvent = EventHandler<LoadManagerPageEvent>()

    private val pages = SparseArray<List<Post>>()
    val posts = EventCollection<Post>(ArrayList())

    val currentPost: Post? get() = posts[position]
    var currentPage = 0
        private set
    var position = -1

    private val downloading = LinkedBlockingQueue<Int>()

    suspend fun downloadNextPage(context: Context) = downloadPage(context, currentPage + 1)
    suspend fun downloadPage(context: Context, page: Int): List<Post>? {
        println("Download $page")
        return withContext(Dispatchers.IO) {
            val p = pages[page]
            if (p == null) {
                if (downloading.contains(page)) return@withContext awaitPage(page)

                downloading += page
                val posts = Api.getPosts(page, tags)
                if (posts != null) {
                    pages.put(page, posts)
                    downloadManagerPageEvent.trigger(DownloadManagerPageEvent(context, page, posts))
                }
                downloading.remove(page)
            }
            pages[page]
        }
    }

    private val loadingMutex = Mutex()
    suspend fun loadNextPage(context: Context): List<Post>? {
        val page = currentPage + 1
        loadingMutex.withLock {
            println("Load ${currentPage + 1}")
            var p = pages[currentPage + 1]
            withContext(Dispatchers.IO) {
                if (p == null) p = downloadPage(context, currentPage + 1)
                if (p != null) {
                    ++currentPage
                    val filtered = p.filterNewPosts()
                    withContext(Dispatchers.Main) {
                        posts += filtered
                        loadManagerPageEvent.trigger(LoadManagerPageEvent(context, p))
                    }
                }
            }
        }
        return pages[page]
    }

    private suspend fun awaitPage(page: Int): List<Post>? {
        var list: List<Post>? = null
        val listener = downloadManagerPageEvent.registerListener {
            if (it.page == page) {
                list = it.posts
                it.deleteListener = true
            }
        }
        return withContext(Dispatchers.IO) {
            val timeout = System.currentTimeMillis()
            while (list == null && System.currentTimeMillis() - timeout < 3000)
                delay(50)
            downloadManagerPageEvent.removeListener(listener)
            list
        }
    }

    private fun List<Post>.filterNewPosts(): List<Post> {
        val lastPostID = if (posts.isNotEmpty()) posts.last().id else Integer.MAX_VALUE
        return this.takeLastWhile { lastPostID > it.id }
    }

    suspend fun reset() {
        pages.clear()
        position = -1
        currentPage = 0
        withContext(Dispatchers.Main) { posts.clear() }
    }

    override fun equals(other: Any?): Boolean {
        return if (other != null && other is Manager)
            tagString == other.tagString
        else false
    }
}

class DownloadManagerPageEvent(val context: Context, val page: Int, val posts: List<Post>) : Event()
class LoadManagerPageEvent(val context: Context, val newPage: List<Post>) : Event()