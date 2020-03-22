package de.yochyo.yummybooru.layout.activities.previewactivity

import android.content.Context
import android.view.Menu
import de.yochyo.eventcollection.events.OnUpdateEvent
import de.yochyo.eventmanager.Listener
import de.yochyo.yummybooru.R
import de.yochyo.yummybooru.api.entities.Tag
import de.yochyo.yummybooru.database.db
import de.yochyo.yummybooru.layout.menus.Menus
import de.yochyo.yummybooru.utils.general.drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ActionBarListener(val context: Context, tag: String, menu: Menu) {
    private var registered: Boolean = false

    private val listener = Listener.create<OnUpdateEvent<Tag>> {
        val tag = it.collection.find { it.name == tag }
        GlobalScope.launch(Dispatchers.Main) {
            Menus.initPreviewMenu(context, menu, tag)
        }
    }

    fun registerListeners() {
        if (!registered){
            registered = true
            context.db.tags.onUpdate.registerListener(listener)
        }

    }

    fun unregisterListeners() {
        if (registered) {
            registered = false
            context.db.tags.onUpdate.removeListener(listener)
        }
    }

}