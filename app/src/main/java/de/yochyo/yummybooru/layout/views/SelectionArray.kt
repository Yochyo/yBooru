package de.yochyo.yummybooru.layout.views

import android.util.SparseBooleanArray
import android.util.SparseIntArray

class SelectionArray {
    private val selected = SparseBooleanArray()
    val size get() =  selected.size()
    fun isEmpty() = selected.size() == 0
    fun put(i: Int) = selected.append(i, true)
    fun remove(i: Int) = selected.delete(i)
    fun isSelected(position: Int) = selected.get(position, false)
    fun clear() = selected.clear()
}