package de.yochyo.yummybooru.layout.activities.pictureactivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import de.yochyo.booruapi.objects.Post
import de.yochyo.booruapi.objects.Tag
import de.yochyo.eventcollection.events.OnUpdateEvent
import de.yochyo.eventmanager.Listener
import de.yochyo.yummybooru.R
import de.yochyo.yummybooru.database.db
import de.yochyo.yummybooru.layout.views.mediaview.MediaView
import de.yochyo.yummybooru.utils.ManagerWrapper
import de.yochyo.yummybooru.utils.general.downloadAndSaveImage
import de.yochyo.yummybooru.utils.general.getCurrentManager
import de.yochyo.yummybooru.utils.general.getOrRestoreManager
import de.yochyo.yummybooru.utils.general.setCurrentManager
import kotlinx.android.synthetic.main.activity_picture.*
import kotlinx.android.synthetic.main.picture_activity_drawer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PictureActivity : AppCompatActivity() {

    companion object {
        fun startActivity(context: Context, manager: ManagerWrapper) {
            context.setCurrentManager(manager)
            context.startActivity(Intent(context, PictureActivity::class.java))
        }
    }

    lateinit var m: ManagerWrapper

    private lateinit var tagRecyclerView: RecyclerView
    private lateinit var tagInfoAdapter: TagInfoAdapter

    private lateinit var pictureAdapter: PictureAdapter

    private val managerListener = Listener<OnUpdateEvent<Post>>
    { GlobalScope.launch(Dispatchers.Main) { this@PictureActivity.pictureAdapter.updatePosts() } }


    //TODO wird onDetach automatisch aufgerufen wenn die activity pausiert wird?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picture)
        setSupportActionBar(toolbar_picture2)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        nav_view_picture.bringToFront()

        tagRecyclerView = nav_view_picture.findViewById(R.id.recycle_view_info)
        tagInfoAdapter = TagInfoAdapter(this).apply { tagRecyclerView.adapter = this }
        tagRecyclerView.layoutManager = LinearLayoutManager(this)


        GlobalScope.launch(Dispatchers.Main) {
            restoreManager(savedInstanceState)
            with(view_pager2) {
                pictureAdapter = PictureAdapter(this@PictureActivity).apply { this@with.adapter = this }
                this.offscreenPageLimit = db.preloadedImages
                m.posts.registerOnUpdateListener(managerListener)
                pictureAdapter.updatePosts()
                m.currentPost?.updateCurrentTags()
                setCurrentItem(m.position, false)
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                        if (positionOffset == 0.0F && m.position != position) {
                            m.position = position
                            m.currentPost?.updateCurrentTags()
                        }
                    }

                    private var lastSelected = m.position

                    override fun onPageSelected(position: Int) {
                        if (lastSelected != -1) {
                            getMediaView(lastSelected)?.pause()
                        }
                        getMediaView(position)?.resume()
                        lastSelected = position
                        if (position + 3 >= m.posts.size - 1) GlobalScope.launch { m.downloadNextPage() }
                    }

                })
            }

        }
    }

    private fun getMediaView(position: Int): MediaView? {
        val child = (view_pager2.findViewWithTag<View>(position) as ViewGroup?)?.getChildAt(0)
        return if (child is MediaView) child
        else null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::m.isInitialized) {
            outState.putString("name", m.toString())
            outState.putInt("position", m.position)
            outState.putInt("id", m.posts.get(if (m.position == -1) 0 else m.position).id)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.picture_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.show_info -> drawer_picture2.openDrawer(GravityCompat.END)
            R.id.save -> m.currentPost?.apply { downloadAndSaveImage(this@PictureActivity, this) }
            R.id.share -> {
                val post = m.currentPost
                if (post != null) {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, if (post.extention == "zip" && db.downloadWebm) post.fileSampleURL else post.fileURL)
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(intent, null))
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        m.posts.removeOnUpdateListener(managerListener)
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (this::m.isInitialized)
            getMediaView(m.position)?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (this::m.isInitialized)
            getMediaView(m.position)?.resume()
    }

    private fun Post.updateCurrentTags() {
        supportActionBar?.title = id.toString()

        GlobalScope.launch {
            val sorted = tags.sortedWith { o1, o2 ->
                fun sortedType(type: Int): Int {
                    return when (type) {
                        Tag.ARTIST -> 0
                        Tag.COPYPRIGHT -> 1
                        Tag.CHARACTER -> 2
                        Tag.GENERAL -> 3
                        Tag.META -> 4
                        else -> 5
                    }
                }

                val sortedType1 = sortedType(o1.type)
                val sortedType2 = sortedType(o2.type)
                if (sortedType1 == sortedType2) o1.name.compareTo(o2.name)
                else sortedType1 - sortedType2
            }
            withContext(Dispatchers.Main) {
                tagInfoAdapter.updateInfoTags(sorted)
                tagRecyclerView.scrollToPosition(0)
            }
        }
    }

    private suspend fun restoreManager(savedInstanceState: Bundle?) {
        withContext(Dispatchers.IO) {
            val oldTags = savedInstanceState?.getString("name")
            val oldPos = savedInstanceState?.getInt("position")
            val oldId = savedInstanceState?.getInt("id")
            if (oldTags != null && oldPos != null && oldId != null) m = getOrRestoreManager(oldTags, oldId, oldPos)
            else m = getCurrentManager()!!
        }
    }
}