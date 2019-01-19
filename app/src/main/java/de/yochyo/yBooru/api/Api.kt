package de.yochyo.yBooru.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.yochyo.yBooru.database
import de.yochyo.yBooru.utils.cache
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


object Api {
    val limit = 30
    private val downloading = ArrayList<String>(20)


    suspend fun downloadImage(context: Context, url: String, id: String): Bitmap {
        if (downloading.contains(id))
            return context.cache.awaitPicture(id)
        else {
            downloading += id
            val bitmap = BitmapFactory.decodeStream(URL(url).openStream()).apply { context.cache.cacheBitmap(id, this) }
            downloading -= id
            return bitmap
        }
    }

    suspend fun getPosts(context: Context, page: Int, vararg tags: String): List<Post> {//TODO rating ist safeSearch
        var url = "https://danbooru.donmai.us/posts.json?limit=$limit&page=$page"
        if (tags.isNotEmpty()) {
            url += "&tags="
            for (tag in tags)
                url += "$tag "
            url = url.substring(0, url.length - 1)
        }
        val json = getJson(url)
        val array = ArrayList<Post>(limit)
        for (i in 0 until json.length()) {
            val post = Post.getPostFromJson(json.getJSONObject(i))
            if (post != null)
                array += post
        }
        if (context.database.r18) return array.filter { it.rating == "s" }
        else return array
    }

    private suspend fun getJson(urlToRead: String): JSONArray {
        var array: JSONArray? = null
        val job = GlobalScope.async {
            val result = StringBuilder()
            val url = URL(urlToRead)
            val conn = url.openConnection() as HttpURLConnection
            conn.addRequestProperty("User-Agent", "Mozilla/5.00")
            conn.requestMethod = "GET"
            val rd = BufferedReader(InputStreamReader(conn.inputStream))
            var line: String? = rd.readLine()
            while (line != null) {
                result.append(line)
                line = rd.readLine()
            }
            rd.close()
            array = JSONArray(result.toString())
        }
        job.join()
        return array!!
    }

}