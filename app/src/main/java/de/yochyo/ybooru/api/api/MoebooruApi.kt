package de.yochyo.ybooru.api.api

import de.yochyo.ybooru.api.Post
import de.yochyo.ybooru.database.entities.Server
import de.yochyo.ybooru.database.entities.Tag
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class MoebooruApi(url: String) : Api(url) {

    override val name: String = "moebooru"
    override fun urlGetTag(name: String): String = "${url}tag.json?name=*$name" //TODO funktioniert das?
    override fun urlGetTags(beginSequence: String): String {
        return "${url}tag.json?name=$beginSequence*&limit=${Api.searchTagLimit}"
    }

    override fun urlGetPosts(page: Int, tags: Array<String>, limit: Int): String {
        return "${url}post.json?limit=$limit&page=$page&login=${Server.currentServer.userName}&password_hash=${Server.currentServer.passwordHash}"
    }

    override fun urlGetNewest(): String = "${url}post.json?limit=1&page=1"

    override fun getPostFromJson(json: JSONObject): Post? {
        try {
            val fileURL = json.getString("file_url")
            val id = json.getInt("id")

            return object : Post {
                override val id = id
                override val width = json.getInt("jpeg_width")
                override val height = json.getInt("jpeg_height")
                override val extension = fileURL.substring(fileURL.lastIndexOf(".") + 1)
                override val rating = json.getString("rating")
                override val fileSize = json.getInt("file_size")
                override val fileURL = fileURL
                override val fileSampleURL = json.getString("sample_url")
                override val filePreviewURL = json.getString("preview_url")
                override val tags: List<Tag> by lazy {
                    var result : List<Tag> = emptyList()
                    runBlocking {
                        GlobalScope.async { result = getTagsfromURL(getURLSourceLines("${Server.currentServer.url}post/show/$id")) }.await()
                    }
                    result
                } //TODO

                override fun toString(): String {
                    return "[$id] [${width}x$height]\nTags: $tags \n$fileURL\n$fileSampleURL\n$filePreviewURL"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getTagsfromURL(lines: ArrayList<String>): List<Tag> {
        val tags = ArrayList<Tag>()
        fun getCurrentTagType(type: String): Int {
            if (type.contains("tag-type-general")) return Tag.GENERAL
            if (type.contains("tag-type-artist")) return Tag.ARTIST
            if (type.contains("tag-type-copyright")) return Tag.COPYPRIGHT
            if (type.contains("tag-type-character")) return Tag.CHARACTER
            return Tag.UNKNOWN
        }

        fun String.startWithIgnoreSpace(start: String): Boolean {
            val a = toCharArray()
            for (i in 0 until a.size) {
                if (a[i] != ' ')
                    return startsWith(start, startIndex = i)
            }
            return false
        }

        var nextLine = false
        val builder = StringBuilder()
        for (line in lines) {
            if (line.startWithIgnoreSpace("<ul id=\"tag-sideb")) {
                nextLine = true
                continue
            }
            if (nextLine)  //hier wird die Tag-Tabelle in einen String gesammelt
                if (line.startWithIgnoreSpace("</ul>"))
                    break
                else builder.append(line)
        }

        //Die Tags werden aus der Tabelle extrahiert
        val splitLines = builder.toString().split("</li>")
        for (l in splitLines) {
            try {
                val subStringType = l.substring(l.indexOf("<li class=\"") + 11)
                val type = getCurrentTagType(subStringType.substring(0, subStringType.indexOf("\"")))

                if (type != Tag.UNKNOWN) {
                    val nameSubstring = subStringType.substring(subStringType.indexOf("href=\"/post?") + 12)
                    val name = nameSubstring.substring(nameSubstring.indexOf(">") + 1, nameSubstring.indexOf("<"))
                    tags += Tag(name, type)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return tags
    }

    override fun getTagFromJson(json: JSONObject): Tag? {
        return try {
            var type = json.getInt("type")
            if (type !in 0..5)
                type = Tag.UNKNOWN
            Tag(json.getString("name"), type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}