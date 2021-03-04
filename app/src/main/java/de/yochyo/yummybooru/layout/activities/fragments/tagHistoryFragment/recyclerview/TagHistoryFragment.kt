package de.yochyo.yummybooru.layout.activities.fragments.tagHistoryFragment.recyclerview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.yochyo.yummybooru.R
import de.yochyo.yummybooru.layout.activities.fragments.tagHistoryFragment.TagHistoryFragmentViewModel
import de.yochyo.yummybooru.layout.alertdialogs.AddSpecialTagDialog
import de.yochyo.yummybooru.layout.alertdialogs.AddTagDialog
import de.yochyo.yummybooru.utils.commands.Command
import de.yochyo.yummybooru.utils.commands.CommandAddTag
import de.yochyo.yummybooru.utils.general.*
import de.yochyo.yummybooru.utils.observeUntil
import kotlinx.android.synthetic.main.fragment_tag_history.*
import kotlinx.android.synthetic.main.fragment_tag_history.view.*
import kotlinx.android.synthetic.main.main_activity_layout.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TagHistoryFragment : Fragment() {
    private lateinit var tagRecyclerView: RecyclerView
    private lateinit var tagAdapter: TagHistoryFragmentAdapter
    lateinit var tagLayoutManager: LinearLayoutManager


    var onSearchButtonClick: (tags: List<String>) -> Unit = {}

    lateinit var viewModel: TagHistoryFragmentViewModel

    companion object {
        private const val SELECTED = "SELECTED"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(requireActivity()).get(TagHistoryFragmentViewModel::class.java)
        viewModel.init(this, ctx)
        val array = savedInstanceState?.getStringArray(SELECTED)
        if (array != null)
            viewModel.selectedTags.value = array.toList()

        val layout = inflater.inflate(R.layout.fragment_tag_history, container, false) as ViewGroup
        configureTagDrawer(layout)

        tagRecyclerView = layout.recycler_view_search
        tagRecyclerView.layoutManager = LinearLayoutManager(ctx).apply { tagLayoutManager = this }
        tagAdapter = TagHistoryFragmentAdapter(this).apply { tagRecyclerView.adapter = this }

        registerObservers()

        return layout
    }

    fun registerObservers() {
        viewModel.tags.observe(viewLifecycleOwner, { tagAdapter.update(it) })
        viewModel.selectedTags.observe(viewLifecycleOwner, { tagAdapter.notifyDataSetChanged() })

        /*
        viewModel.tags.withValue(viewLifecycleOwner){
            GlobalScope.launch {
                val s = viewModel.server
                var c = TagCollection("Test33", viewModel.server.id)
                c = c.copy(id = ctx.db.addTagCollection(c).toInt())
                ctx.db.addTagsToCollection(c, it)
            }
        }
         */

    }

    private fun configureTagDrawer(layout: ViewGroup) {
        configureDrawerToolbar(layout.findViewById(R.id.search_toolbar))
        layout.findViewById<SearchView>(R.id.tag_filter).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null) viewModel.filter.value = newText
                return true
            }

            override fun onQueryTextSubmit(query: String?) = true
        })

    }

    private fun configureDrawerToolbar(toolbar: Toolbar) {
        toolbar.inflateMenu(R.menu.main_search_nav_menu)
        toolbar.setNavigationIcon(R.drawable.clear)
        toolbar.navigationContentDescription = getString(R.string.deselect_tags)
        toolbar.setNavigationOnClickListener {
            viewModel.selectedTags.value = emptyList()
            Toast.makeText(ctx, getString(R.string.deselected_tags), Toast.LENGTH_SHORT).show()
        }

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.search -> onSearchButtonClick(viewModel.selectedTags.value ?: listOf("*"))
                R.id.add_tag -> {
                    AddTagDialog {
                        GlobalScope.launch(Dispatchers.Main) {
                            val t = viewModel.server.getTag(it.text.toString())
                            if (Command.executeAsync(fragment_tag_history, CommandAddTag(t))) {
                                viewModel.tags.observeUntil(this@TagHistoryFragment, {
                                    val index = it.indexOfFirst { it.name == t.name }
                                    if (index >= 0)
                                        tagLayoutManager.scrollToPositionWithOffset(index, 0)
                                }, { it.find { it.name == t.name } != null })
                            }
                        }
                    }.build(ctx)
                }
                R.id.add_special_tag -> AddSpecialTagDialog().build(fragment_tag_history, viewModel.server)
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray(SELECTED, (viewModel.selectedTags.value ?: emptyList()).toTypedArray())
    }
}