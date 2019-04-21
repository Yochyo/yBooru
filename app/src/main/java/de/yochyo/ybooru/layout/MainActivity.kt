package de.yochyo.ybooru.layout

import android.Manifest
import android.arch.lifecycle.Observer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
import de.yochyo.ybooru.R
import de.yochyo.ybooru.api.Downloader
import de.yochyo.ybooru.api.api.Api
import de.yochyo.ybooru.api.api.DanbooruApi
import de.yochyo.ybooru.api.api.MoebooruApi
import de.yochyo.ybooru.database.Database
import de.yochyo.ybooru.database.db
import de.yochyo.ybooru.database.entities.Server
import de.yochyo.ybooru.database.entities.Subscription
import de.yochyo.ybooru.database.entities.Tag
import de.yochyo.ybooru.layout.alertdialogs.AddServerDialog
import de.yochyo.ybooru.layout.alertdialogs.AddTagDialog
import de.yochyo.ybooru.layout.res.Menus
import de.yochyo.ybooru.manager.Manager
import de.yochyo.ybooru.utils.setColor
import de.yochyo.ybooru.utils.toTagString
import de.yochyo.ybooru.utils.underline
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val selectedTags = ArrayList<String>()
    private lateinit var menu: Menu

    private lateinit var tagAdapter: SearchTagAdapter
    private lateinit var serverAdapter: ServerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlobalScope.launch { Downloader.getInstance(this@MainActivity).clearCache() }
        Api.addApi(DanbooruApi(""))
        Api.addApi(MoebooruApi(""))
        Database.initDatabase(this)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)

        val navLayout = nav_search.findViewById<LinearLayout>(R.id.nav_search_layout)
        initAddTagButton(navLayout.findViewById(R.id.add_search))
        initSearchButton(navLayout.findViewById(R.id.start_search))
        val tagRecyclerView = navLayout.findViewById<RecyclerView>(R.id.recycler_view_search)
        tagRecyclerView.layoutManager = LinearLayoutManager(this)
        tagAdapter = SearchTagAdapter().apply { tagRecyclerView.adapter = this }
        val serverRecyclerView = findViewById<RecyclerView>(R.id.server_recycler_view)
        serverRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
        serverAdapter = ServerAdapter().apply { serverRecyclerView.adapter = this }

        db.tags.observe(this, Observer<TreeSet<Tag>> { t -> if (t != null) tagAdapter.updateTags(t) })
        db.servers.observe(this, Observer<TreeSet<Server>> { s -> if (s != null) serverAdapter.updateServers(s) })
    }

    private fun initAddTagButton(b: Button) {
        b.setOnClickListener {
            AddTagDialog {
                if (db.getTag(it.text.toString()) == null) {
                    GlobalScope.launch(Dispatchers.IO) {
                        val tag = Api.getTag(it.text.toString())
                        launch(Dispatchers.Main) {
                            val newTag: Tag = tag ?: Tag(it.text.toString(), Tag.UNKNOWN)
                            db.addTag(newTag)
                        }
                    }
                }
            }.apply { title = "Add Subscription" }.build(this)
        }
    }

    private fun initSearchButton(b: Button) {
        b.setOnClickListener {
            drawer_layout.closeDrawer(GravityCompat.END)
            if (selectedTags.isEmpty()) PreviewActivity.startActivity(this, "*")
            else PreviewActivity.startActivity(this, selectedTags.toTagString())
        }
    }

    fun fillServerLayoutFields(layout: LinearLayout, server: Server, isSelected: Boolean = false) {
        val text1 = layout.findViewById<TextView>(R.id.server_text1)
        text1.text = server.name
        if (isSelected) text1.setColor(R.color.dark_red)
        else text1.setColor(R.color.violet)
        layout.findViewById<TextView>(R.id.server_text2).text = server.api
        layout.findViewById<TextView>(R.id.server_text3).text = server.userName

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_server -> AddServerDialog { db.addServer(it); Toast.makeText(this, "Add Server", Toast.LENGTH_SHORT).show() }.apply { serverID = db.nextServerID++ }.build(this)
            R.id.search -> drawer_layout.openDrawer(GravityCompat.END)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_subs -> startActivity(Intent(this, SubscriptionActivity::class.java))
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.community -> {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse("https://discord.gg/tbGCHpF")
                startActivity(i)
            }
            R.id.nav_help -> Toast.makeText(this, "Join Discord", Toast.LENGTH_SHORT).show()
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) drawer_layout.closeDrawer(GravityCompat.START)
        else if (drawer_layout.isDrawerOpen(GravityCompat.END)) drawer_layout.closeDrawer(GravityCompat.END)
        else super.onBackPressed()
    }

    private inner class SearchTagAdapter : RecyclerView.Adapter<SearchTagViewHolder>() {
        private var tags = TreeSet<Tag>()

        fun updateTags(set: TreeSet<Tag>) {
            tags = set
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchTagViewHolder = SearchTagViewHolder((LayoutInflater.from(parent.context).inflate(R.layout.search_item_layout, parent, false) as Toolbar)).apply {
            val check = toolbar.findViewById<CheckBox>(R.id.search_checkbox)
            toolbar.inflateMenu(R.menu.activity_main_search_menu)
            toolbar.setOnClickListener {
                if (check.isChecked) selectedTags.remove(it.findViewById<TextView>(R.id.search_textview).text)
                else selectedTags.add(it.findViewById<TextView>(R.id.search_textview).text.toString())
                check.isChecked = !check.isChecked
            }
            check.setOnClickListener {
                if (!(it as CheckBox).isChecked) selectedTags.remove(toolbar.findViewById<TextView>(R.id.search_textview).text)
                else selectedTags.add(toolbar.findViewById<TextView>(R.id.search_textview).text.toString())
            }
            toolbar.setOnMenuItemClickListener {
                val tag = tags.elementAt(adapterPosition)
                when (it.itemId) {
                    R.id.main_search_favorite_tag -> db.changeTag(tag.apply { isFavorite = !isFavorite })
                    R.id.main_search_subscribe_tag -> {
                        if (db.getSubscription(tag.name) == null) {
                            GlobalScope.launch { val currentID = Api.newestID();launch(Dispatchers.Main) { db.addSubscription(Subscription(tag.name, tag.type, currentID)) } }
                            Toast.makeText(this@MainActivity, "Subscribed ${tag.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            db.deleteSubscription(tag.name)
                            Toast.makeText(this@MainActivity, "Unsubscribed ${tag.name}", Toast.LENGTH_SHORT).show()
                        }
                        notifyItemChanged(adapterPosition)
                    }
                    R.id.main_search_delete_tag -> {
                        db.deleteTag(tag.name)
                        selectedTags.remove(tag.name)
                    }
                }
                true
            }
        }

        override fun getItemCount(): Int = tags.size
        override fun onBindViewHolder(holder: SearchTagViewHolder, position: Int) {
            val tag = tags.elementAt(position)
            val check = holder.toolbar.findViewById<CheckBox>(R.id.search_checkbox)
            check.isChecked = selectedTags.contains(tag.name)
            val textView = holder.toolbar.findViewById<TextView>(R.id.search_textview)
            textView.text = tag.name
            textView.setColor(tag.color)
            textView.underline(tag.isFavorite)

            Menus.initMainSearchTagMenu(holder.toolbar.menu, tag)
        }
    }

    private inner class ServerAdapter : RecyclerView.Adapter<ServerViewHolder>() {
        var servers = TreeSet<Server>()
        override fun getItemCount(): Int = servers.size

        fun updateServers(servers: TreeSet<Server>) {
            this.servers = servers
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, position: Int): ServerViewHolder {
            val holder = ServerViewHolder((LayoutInflater.from(parent.context).inflate(R.layout.server_item_layout, parent, false) as LinearLayout))
            holder.layout.setOnClickListener {
                val server = servers.elementAt(holder.adapterPosition)
                server.select()
                notifyDataSetChanged()
                selectedTags.clear()
                Toast.makeText(this@MainActivity, "Selected Server", Toast.LENGTH_SHORT).show()
            }
            holder.layout.setOnLongClickListener {
                val server = servers.elementAt(holder.adapterPosition)
                AddServerDialog { db.changeServer(it);(if (Server.currentServer == it) it.select()) }.apply {
                    serverID = server.id
                    nameText = server.name
                    apiText = server.api
                    urlText = server.url
                    userText = server.userName
                    passwordText = server.password
                    message = "Edit Server"
                    enableR18 = server.enableR18Filter
                }.build(this@MainActivity)
                true
            }
            return holder
        }

        override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
            val server = servers.elementAt(position)
            fillServerLayoutFields(holder.layout, server, server.isSelected)
        }
    }

    private inner class ServerViewHolder(val layout: LinearLayout) : RecyclerView.ViewHolder(layout)
    private inner class SearchTagViewHolder(val toolbar: Toolbar) : RecyclerView.ViewHolder(toolbar)
}
