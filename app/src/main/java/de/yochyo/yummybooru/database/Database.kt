package de.yochyo.yummybooru.database

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters
import android.arch.persistence.room.migration.Migration
import android.content.Context
import android.content.SharedPreferences
import de.yochyo.yummybooru.api.downloads.Manager
import de.yochyo.yummybooru.api.entities.*
import de.yochyo.yummybooru.database.converter.DateConverter
import de.yochyo.yummybooru.events.events.*
import de.yochyo.yummybooru.utils.createDefaultSavePath
import de.yochyo.yummybooru.utils.liveData.LiveTree
import de.yochyo.yummybooru.utils.lock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@android.arch.persistence.room.Database(entities = [Tag::class, Subscription::class, Server::class], version = 2)
@TypeConverters(DateConverter::class)
abstract class Database : RoomDatabase() {
    lateinit var prefs: SharedPreferences

    companion object {
        var instance: Database? = null
        fun initDatabase(context: Context): Database {
            if (instance == null) instance = Room.databaseBuilder(context.applicationContext,
                    Database::class.java, "db")
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            for (s in DefaultServerExeq.all)
                                db.execSQL(s)
                        }
                    })
                    .addMigrations(*Migrations.all).build()
            instance!!.prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            instance!!.initServer(context)

            return instance!!
        }

    }

    val servers = LiveTree<Server>()
    val tags = LiveTree<Tag>()
    val subs = LiveTree<Subscription>()

    fun initServer(context: Context) {
        GlobalScope.launch {
            val se: List<Server> = serverDao.getAllServers()
            withContext(Dispatchers.Main) {
                servers.clear()
                servers += se
                Server.currentServer.select(context)
            }
        }
    }

    fun initTags(context: Context, serverID: Int) {
        GlobalScope.launch {
            val t = tagDao.getAllTags().filter { it.serverID == serverID }
            withContext(Dispatchers.Main) {
                tags.clear()
                tags += t
                Server.currentServer.updateMissingTypeTags(context)
            }
        }
    }

    fun initSubscriptions(context: Context, serverID: Int) {
        GlobalScope.launch {
            val s = subDao.getAllSubscriptions().filter { it.serverID == serverID }
            withContext(Dispatchers.Main) {
                subs.clear()
                subs += s
                Server.currentServer.updateMissingTypeSubs(context)
            }
        }
    }

    fun getTag(name: String) = tags.find { it.name == name }
    suspend fun addTag(context: Context, tag: Tag): Tag {
        return withContext(Dispatchers.Main) {
            val t = getTag(tag.name)
            if (t == null) {
                AddTagEvent.trigger(AddTagEvent(context, tag))
                synchronized(lock) { tags += tag }
                withContext(Dispatchers.Default) { tagDao.insert(tag) }
                return@withContext tag
            } else t
        }
    }

    suspend fun deleteTag(context: Context, name: String) {
        withContext(Dispatchers.Main) {
            val tag = tags.find { it.name == name }
            if (tag != null) {
                DeleteTagEvent.trigger(DeleteTagEvent(context, tag))
                synchronized(lock) { tags.remove(tag) }
                withContext(Dispatchers.Default) { tagDao.delete(tag) }
            }
        }
    }

    suspend fun changeTag(context: Context, changedTag: Tag) {
        withContext(Dispatchers.Main) {
            val tag = tags.find { it.name == changedTag.name }
            if (tag != null) {
                ChangeTagEvent.trigger(ChangeTagEvent(context, tag, changedTag))
                tags.remove(tag)
                tags.add(changedTag)
                withContext(Dispatchers.Default) { tagDao.update(changedTag) }
            }
        }
    }

    fun getSubscription(name: String) = subs.find { it.name == name }
    suspend fun addSubscription(context: Context, sub: Subscription) {
        withContext(Dispatchers.Main) {
            if (getSubscription(sub.name) == null) {
                AddSubEvent.trigger(AddSubEvent(context, sub))
                synchronized(lock) { subs += sub }
                withContext(Dispatchers.Default) { subDao.insert(sub) }
            }
        }
    }

    suspend fun deleteSubscription(context: Context, name: String) {
        withContext(Dispatchers.Main) {
            val sub = subs.find { it.name == name }
            if (sub != null) {
                DeleteSubEvent.trigger(DeleteSubEvent(context, sub))
                synchronized(lock) { subs.remove(sub) }
                withContext(Dispatchers.Default) { subDao.delete(sub) }
            }
        }
    }

    suspend fun changeSubscription(context: Context, changedSub: Subscription) {
        withContext(Dispatchers.Main) {
            val sub = subs.find { it.name == changedSub.name }
            if (sub != null) {
                ChangeSubEvent.trigger(ChangeSubEvent(context, sub, changedSub))
                subs.remove(sub)
                subs.add(changedSub)
                withContext(Dispatchers.Default) { subDao.update(changedSub) }
            }
        }
    }

    fun getServer(id: Int) = servers.find { it.id == id }
    suspend fun addServer(context: Context, server: Server, id: Int = nextServerID++) {
        withContext(Dispatchers.Main) {
            val s = getServer(server.id)
            if (s == null) {
                AddServerEvent.trigger(AddServerEvent(context, server))
                synchronized(lock) { servers += server.copy(id = id) }
                withContext(Dispatchers.Default) { serverDao.insert(server) }
            }
        }
    }

    suspend fun deleteServer(context: Context, id: Int) {
        withContext(Dispatchers.Main) {
            val s = servers.find { id == it.id }
            if (s != null) {
                DeleteServerEvent.trigger(DeleteServerEvent(context, s))
                if (currentServerID == id) s.unselect()
                synchronized(lock) { servers.remove(s) }
                withContext(Dispatchers.Default) { serverDao.delete(s) }
            }
        }
    }

    suspend fun changeServer(context: Context, server: Server) {
        withContext(Dispatchers.Main) {
            val s = servers.find { it.id == server.id }
            if (s != null) {
                ChangeServerEvent.trigger(ChangeServerEvent(context, server, s))
                val wasCurrentServer = Server.currentServer == server
                synchronized(lock) {
                    servers -= s
                    servers += server
                }
                Manager.resetAll()
                if (wasCurrentServer)
                    server.select(context)
                withContext(Dispatchers.Default) { serverDao.update(server) }
            }
        }
    }

    private var _nextServerID: Int? = null
    var nextServerID: Int
        get() {
            if (_nextServerID == null)
                _nextServerID = prefs.getInt("nextServerID", DefaultServerExeq.all.size)
            return _nextServerID!!
        }
        set(v) {
            _nextServerID = v
            with(prefs.edit()) {
                putInt("nextServerID", v)
                apply()
            }
        }

    private var _limit: Int? = null
    var limit: Int
        get() {
            if (_limit == null) _limit = prefs.getInt("limit", 30)
            return _limit!!
        }
        set(value) {
            _limit = value
            with(prefs.edit()) {
                putInt("limit", value)
                apply()
            }
        }

    private var _currentServerID: Int? = null
    var currentServerID: Int
        get() {
            if (_currentServerID == null) _currentServerID = prefs.getInt("currentServer", 0)
            return _currentServerID!!
        }
        set(v) {
            _currentServerID = v
            with(prefs.edit()) {
                putInt("currentServer", v)
                apply()
            }
        }


    private var _sortTags: String? = null
    var sortTags: String
        get() {
            if (_sortTags == null) _sortTags = prefs.getString("sortTags", "00")
            return _sortTags!!
        }
        set(value) {
            _sortTags = value
            with(prefs.edit()) {
                putString("sortTags", value)
                apply()
            }
        }
    private var _sortSubs: String? = null
    var sortSubs: String
        get() {
            if (_sortSubs == null) _sortSubs = prefs.getString("sortSubs", "00")
            return _sortSubs!!
        }
        set(value) {
            _sortSubs = value
            with(prefs.edit()) {
                putString("sortSubs", value)
                apply()
            }
        }
    private var _downloadOriginal: Boolean? = null
    var downloadOriginal: Boolean
        get() {
            if (_downloadOriginal == null) _downloadOriginal = prefs.getBoolean("downloadOriginal", true)
            return _downloadOriginal!!
        }
        set(v) {
            _downloadOriginal = v
            with(prefs.edit()) {
                putBoolean("downloadOriginal", v)
                apply()
            }
        }

    private var _savePath: String? = null
    var savePath: String
        get() {
            if (_savePath == null) _savePath = prefs.getString("savePath", createDefaultSavePath())
            return _savePath!!
        }
        set(v) {
            _savePath = v
            with(prefs.edit()) {
                putString("savePath", v)
                apply()
            }
        }

    var sortTagsByFavorite: Boolean
        get() = sortTags.first() == '1'
        set(v) {
            sortTags = "${sortTags.first()}$v"
        }
    var sortTagsByAlphabet: Boolean
        get() = sortTags.last() == '1'
        set(v) {
            sortTags = "$v${sortTags.last()}"
        }

    var sortSubsByFavorite: Boolean
        get() = sortSubs.first() == '1'
        set(v) {
            sortSubs = "${sortSubs.first()}$v"
        }
    var sortSubsByAlphabet: Boolean
        get() = sortSubs.last() == '1'
        set(v) {
            sortSubs = "$v${sortSubs.last()}"
        }

    suspend fun deleteEverything() {
        withContext(Dispatchers.Default) {
            clearAllTables()
            withContext(Dispatchers.Main) {
                servers.clear()
                tags.clear()
                subs.clear()
            }
        }
        val p = prefs.all
        with(prefs.edit()) {
            for (prefToReset in p.entries)
                remove(prefToReset.key).apply()
        }
    }

    abstract val subDao: SubscriptionDao
    abstract val tagDao: TagDao
    abstract val serverDao: ServerDao
}

val db: Database get() = Database.instance!!


private object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
        }
    }


    val all = arrayOf(MIGRATION_1_2)
}

object DefaultServerExeq {
    val all = ArrayList<String>()

    init {
        all += "INSERT INTO servers (name,api,url,userName,password,enableR18Filter,id) VALUES ('Danbooru', 'danbooru', 'https://danbooru.donmai.us/', '', '', 0, 0);"
        all += "INSERT INTO servers (name,api,url,userName,password,enableR18Filter,id) VALUES ('Konachan', 'moebooru', 'https://konachan.com/', '', '', 0, 1);"
        all += "INSERT INTO servers (name,api,url,userName,password,enableR18Filter,id) VALUES ('Yande.re', 'moebooru', 'https://yande.re/', '', '', 0, 2);"
    }
}