package de.yochyo.yummybooru.utils.commands

import android.content.Context
import de.yochyo.yummybooru.R
import de.yochyo.yummybooru.api.entities.Following
import de.yochyo.yummybooru.api.entities.Tag
import de.yochyo.yummybooru.database.db

class CommandAddTag(private val tag: Tag) : Command {

    override val show: Command.Show = Command.Show.TOAST
    override fun getToastMessage(context: Context): String {
        return context.getString(R.string.added_tag_with_name, tag.name)
    }

    override fun getUndoMessage(context: Context): String {
        return context.getString(R.string.undo_add_tag_with_name, tag.name)
    }

    override fun run(context: Context): Boolean {
        return context.db.addTag(tag) > 0
    }

    override fun undo(context: Context): Boolean {
        return context.db.removeTag(tag) > 0
    }
}

class CommandDeleteTag(private val tag: Tag) : Command {
    override val show = Command.Show.SNACKBAR
    override fun getToastMessage(context: Context): String {
        return context.getString(R.string.deleted_tag_with_name, tag.name)
    }

    override fun getUndoMessage(context: Context): String {
        return context.getString(R.string.add_tag_with_name, tag.name)
    }

    override fun run(context: Context): Boolean {
        return context.db.removeTag(tag) > 0
    }

    override fun undo(context: Context): Boolean {
        return context.db.addTag(tag) > 0
    }
}

class CommandFavoriteTag(private val tag: Tag, private val value: Boolean) : Command {
    private val copy = tag.copy(isFavorite = value)

    override val show = Command.Show.SNACKBAR
    override fun getToastMessage(context: Context): String {
        return if (value) context.getString(R.string.favorited_tag_with_name, tag.name)
        else context.getString(R.string.unfavorited_tag_with_name, tag.name)
    }

    override fun getUndoMessage(context: Context): String {
        return if (value) context.getString(R.string.unfavorite_tag_with_name, tag.name)
        else context.getString(R.string.favorite_tag_with_name, tag.name)
    }

    override fun run(context: Context): Boolean {
        return context.db.updateTag(copy) > 0
    }

    override fun undo(context: Context): Boolean {
        return context.db.updateTag(tag) > 0
    }
}

class CommandUpdateFollowingTagData(private val tag: Tag, val following: Following?) : Command {
    private val copy = tag.copy(following = following)

    override val show = Command.Show.SNACKBAR
    override fun getToastMessage(context: Context): String {
        return if (following != null) context.getString(R.string.followed_tag_with_name, tag.name)
        else context.getString(R.string.unfollowed_tag_with_name, tag.name)
    }

    override fun getUndoMessage(context: Context): String {
        if (following == null) context.getString(R.string.follow_tag_with_name, tag.name)
        return context.getString(R.string.unfollow_tag_with_name, tag.name)
    }

    override fun run(context: Context): Boolean {
        return context.db.updateTag(copy) > 0
    }

    override fun undo(context: Context): Boolean {
        return context.db.updateTag(tag) > 0
    }
}

class CommandUpdateSeveralFollowingTagData(private val tagFollowingDataPairs: List<Pair<Tag, Following?>>) : Command {
    private val copy = tagFollowingDataPairs.map { it.first.copy(following = it.second) }

    override val show = Command.Show.SNACKBAR
    override fun getToastMessage(context: Context): String {
        return context.getString(R.string.updated_tags)
    }

    override fun getUndoMessage(context: Context): String {
        return context.getString(R.string.undo_updating_tags)
    }

    override fun run(context: Context): Boolean {
        return context.db.updateTags(copy) > 0
    }

    override fun undo(context: Context): Boolean {
        return context.db.updateTags(tagFollowingDataPairs.map { it.first }) > 0
    }
}