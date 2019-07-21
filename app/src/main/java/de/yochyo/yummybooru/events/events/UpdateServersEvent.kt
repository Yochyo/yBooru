package de.yochyo.yummybooru.events.events

import android.content.Context
import de.yochyo.eventmanager.Event
import de.yochyo.eventmanager.EventHandler
import de.yochyo.yummybooru.api.entities.Server

class UpdateServersEvent(val context: Context, val servers: Collection<Server>): Event() {
    companion object: EventHandler<UpdateServersEvent>()
}