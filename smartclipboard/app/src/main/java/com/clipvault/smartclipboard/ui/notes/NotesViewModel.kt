package com.clipvault.smartclipboard.ui.notes

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.smartclipboard.app
import com.clipvault.smartclipboard.data.db.CategoryEntity
import com.clipvault.smartclipboard.data.db.NoteEntity
import com.clipvault.smartclipboard.data.prefs.NotesOrder
import com.clipvault.smartclipboard.data.repo.NoteListFilter
import com.clipvault.smartclipboard.data.repo.NoteQuery
import com.clipvault.smartclipboard.util.ClipboardEcho
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NotesUi {
    data object Loading : NotesUi
    data class Ready(val notes: List<NoteEntity>) : NotesUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.app().repository
    private val prefs = app.app().preferences

    val filter = MutableStateFlow(NoteListFilter.ALL)
    val categoryId = MutableStateFlow<Long?>(null)
    val search = MutableStateFlow("")
    val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val dragOrder = MutableStateFlow<List<Long>?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val categories: StateFlow<List<CategoryEntity>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val query = combine(filter, categoryId, search) { f, c, s -> NoteQuery(f, c, s) }

    val notes: StateFlow<NotesUi> = query.flatMapLatest { repo.observeNotes(kotlinx.coroutines.flow.flowOf(it)) }
        .combine(dragOrder) { list, dragging ->
            if (dragging == null) list
            else dragging.mapNotNull { id -> list.find { it.id == id } }
        }
        .map<List<NoteEntity>, NotesUi> { NotesUi.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUi.Loading)

    fun manualOrderEnabled(): Boolean =
        filter.value != NoteListFilter.TRASH && prefs.notesOrder() == NotesOrder.MANUAL && search.value.isBlank()

    fun setFilter(value: NoteListFilter) {
        filter.value = value
        clearSelection()
        dragOrder.value = null
    }

    fun selectCategory(id: Long?) {
        categoryId.value = id
        clearSelection()
    }

    fun toggleSelect(id: Long) {
        selected.value = selected.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun startSelection(id: Long) {
        selected.value = selected.value + id
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun nudge(id: Long, delta: Int) {
        if (!manualOrderEnabled()) {
            _messages.tryEmit(getApplication<Application>().getString(com.clipvault.smartclipboard.R.string.reorder_requires_manual))
            return
        }
        val current = (notes.value as? NotesUi.Ready)?.notes?.map { it.id } ?: return
        val ids = (dragOrder.value ?: current).toMutableList()
        val index = ids.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in ids.indices) return
        val swapped = ids[index]
        ids[index] = ids[target]
        ids[target] = swapped
        dragOrder.value = ids
    }

    fun commitOrder() {
        val ids = dragOrder.value ?: return
        viewModelScope.launch {
            runCatching { repo.reorder(ids) }
                .onFailure { _messages.emit("Couldn't save the new order") }
            dragOrder.value = null
        }
    }

    fun copyNotes(ids: List<Long>) {
        val ready = (notes.value as? NotesUi.Ready)?.notes ?: return
        val text = ready.filter { it.id in ids }.joinToString(prefs.mergeSeparator()) { it.body }
        if (text.isBlank()) return
        copyToClipboard(text)
        _messages.tryEmit(getApplication<Application>().getString(com.clipvault.smartclipboard.R.string.copied))
    }

    fun trashSelected() = launchIds { repo.moveToTrash(it) }
    fun restoreSelected() = launchIds { repo.restore(it) }
    fun deleteSelected() = launchIds { repo.deleteForever(it) }
    fun favoriteSelected(favorite: Boolean) {
        val ids = selected.value.toList()
        viewModelScope.launch {
            ids.forEach { repo.setFavorite(it, favorite) }
            clearSelection()
        }
    }

    fun mergeSelected(trashSources: Boolean) {
        val ids = selected.value.toList()
        viewModelScope.launch {
            runCatching { repo.merge(ids, trashSources) }
                .onSuccess { _messages.emit("Notes merged") }
                .onFailure { _messages.emit("Couldn't merge those notes") }
            clearSelection()
        }
    }

    fun moveSelectedTo(categoryId: Long?) {
        val ids = selected.value.toList()
        viewModelScope.launch {
            repo.setCategory(ids, categoryId)
            clearSelection()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            runCatching { repo.emptyTrash() }
                .onFailure { _messages.emit("Couldn't empty the trash") }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            runCatching { repo.addCategory(name) }
                .onFailure { _messages.emit("Couldn't add that category") }
        }
    }

    fun renameCategory(id: Long, name: String) {
        viewModelScope.launch { repo.renameCategory(id, name) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { repo.deleteCategory(id) }
    }

    private fun launchIds(block: suspend (List<Long>) -> Unit) {
        val ids = selected.value.toList()
        viewModelScope.launch {
            runCatching { block(ids) }
                .onFailure { _messages.emit("Something went wrong") }
            clearSelection()
        }
    }

    private fun copyToClipboard(text: String) {
        ClipboardEcho.suppressNext(text)
        val cm = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("ClipVault", text))
    }
}
