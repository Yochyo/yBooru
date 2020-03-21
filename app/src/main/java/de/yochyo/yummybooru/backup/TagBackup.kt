package de.yochyo.yummybooru.backup

import android.content.Context
import de.yochyo.yummybooru.api.entities.Sub
import de.yochyo.yummybooru.api.entities.Tag
import de.yochyo.yummybooru.database.db
import de.yochyo.yummybooru.utils.general.Logger
import org.json.JSONObject
import java.lang.Exception
import java.util.*

object TagBackup : BackupableEntity<Tag> {
    override fun toJSONObject(e: Tag, context: Context): JSONObject {
        val json = JSONObject()
        json.put("name", e.name)
        json.put("type", e.type)
        json.put("isFavorite", e.isFavorite)
        json.put("creation", e.creation.time)
        json.put("serverID", e.serverID)
        json.put("lastID", e.sub?.lastID ?: -1)
        json.put("lastCount", e.sub?.lastCount ?: -1)
        return json
    }

    override suspend fun restoreEntity(json: JSONObject, context: Context) {
        try{
            var sub: Sub? = Sub(json.getInt("lastID"), json.getInt("lastCount"))
            if(sub?.lastID == -1 && sub.lastCount == -1) sub = null
            context.db.tagDao.insert(
                    Tag(json.getString("name"), json.getInt("type"), json.getBoolean("isFavorite"),
                            0, sub, Date(json.getLong("creation")), json.getInt("serverID")))
        }catch(e: Exception){
            e.printStackTrace()
            Logger.log(e)
        }
    }

}