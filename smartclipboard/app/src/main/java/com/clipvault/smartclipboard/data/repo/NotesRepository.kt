package com.clipvault.smartclipboard.data.repo

import com.clipvault.smartclipboard.data.db.AppDatabase
import com.clipvault.smartclipboard.data.db.CategoryEntity
import com.clipvault.smartclipboard.data.db.NoteEntity
import com.clipvault.smartclipboard.data.prefs.NewNotePosition
import com.clipvault.smartclipboard.data.prefs.NotesOrder
import com.clipvault.smartclipboard.data.prefs.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class NoteListFilter { ALL, FAVORITES, TRASH }

data class NoteQuery(
    val filter: NoteListFilter = NoteListFilter.ALL,
    val categoryId: Long? = null,
    val search: String = "",
)

/**
 * Single writer for notes and categories. UI and the clipboard service both
 * go through here so ordering, duplicate checks and merges stay consistent.
 */
class NotesRepository(
    private val db: AppDatabase,
    private val preferences: UserPreferences,
) {
    private val notes = db.noteDao()
    private val categories = db.categoryDao()

    fun observeCategories(): Flow<List<CategoryEntity>> = categories.observeAll()

    fun observeNotes(query: Flow<NoteQuery>): Flow<List<NoteEntity>> {
        return combine(notes.observeAll(), query) { all, q ->
            val filtered = all.filter { note ->
                val tabOk = when (q.filter) {
                    NoteListFilter.ALL -> !note.isTrash
                    NoteListFilter.FAVORITES -> note.isFavorite && !note.isTrash
                    NoteListFilter.TRASH -> note.isTrash
                }
                val categoryOk = q.categoryId == null || note.categoryId == q.categoryId
                val needle = q.search.trim()
                val searchOk = needle.isEmpty() ||
                    note.title.contains(needle, ignoreCase = true) ||
                    note.body.contains(needle, ignoreCase = true)
                tabOk && categoryOk && searchOk
            }
            sort(filtered, if (q.filter == NoteListFilter.TRASH) NotesOrder.NEWEST else preferences.notesOrder())
        }
    }

    fun observeNote(id: Long): Flow<NoteEntity?> = notes.observeById(id)

    /** Active notes for the IME, already sorted. */
    fun observeKeyboardNotes(categoryId: Long?, order: NotesOrder): Flow<List<NoteEntity>> {
        return notes.observeActiveByPosition().map { list ->
            val scoped = if (categoryId == null) list else list.filter { it.categoryId == categoryId }
            sort(scoped, order)
        }
    }

    suspend fun defaultCategoryId(): Long? = categories.getDefault()?.id

    suspend fun insertManual(title: String, body: String, categoryId: Long?): Long {
        val now = System.currentTimeMillis()
        val position = nextPosition()
        return notes.insert(
            NoteEntity(
                title = title.ifBlank { deriveTitle(body) },
                body = body,
                categoryId = categoryId ?: defaultCategoryId(),
                position = position,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Called by the monitor once a clip has been read. Returns the new id, or
     * null when the preference or duplicate rule said to ignore it.
     */
    suspend fun captureFromClipboard(raw: String): Long? {
        val body = raw.trim()
        if (body.isEmpty()) return null
        if (!preferences.autoAdd()) return null
        if (preferences.skipDuplicates() && notes.findActiveByExactBody(body) != null) return null
        val now = System.currentTimeMillis()
        val position = when (preferences.newNotePosition()) {
            NewNotePosition.TOP -> {
                val min = notes.minActivePosition()
                min - 1
            }
            NewNotePosition.BOTTOM -> notes.maxActivePosition() + 1
        }
        return notes.insert(
            NoteEntity(
                title = deriveTitle(body),
                body = body,
                categoryId = defaultCategoryId(),
                position = position,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateNote(id: Long, title: String, body: String, categoryId: Long?) {
        val existing = notes.getById(id) ?: return
        val now = System.currentTimeMillis()
        notes.update(
            existing.copy(
                title = title.ifBlank { deriveTitle(body) },
                body = body,
                categoryId = categoryId,
                updatedAt = now,
            ),
        )
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        notes.setFavorite(id, favorite, System.currentTimeMillis())
    }

    suspend fun moveToTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        notes.moveAllToTrash(ids, System.currentTimeMillis())
    }

    suspend fun restore(ids: List<Long>) {
        if (ids.isEmpty()) return
        notes.restoreAll(ids, System.currentTimeMillis())
    }

    suspend fun deleteForever(ids: List<Long>) {
        if (ids.isEmpty()) return
        notes.deleteByIds(ids)
    }

    suspend fun emptyTrash() = notes.emptyTrash()

    suspend fun purgeTrashOlderThan(before: Long) = notes.deleteTrashOlderThan(before)

    suspend fun setCategory(ids: List<Long>, categoryId: Long?) {
        val now = System.currentTimeMillis()
        ids.forEach { notes.setCategory(it, categoryId, now) }
    }

    suspend fun reorder(idsInOrder: List<Long>) = notes.replaceManualOrder(idsInOrder)

    suspend fun merge(ids: List<Long>, trashSources: Boolean): Long? {
        if (ids.size < 2) return null
        val selected = notes.getByIds(ids).sortedBy { ids.indexOf(it.id) }
        if (selected.isEmpty()) return null
        val now = System.currentTimeMillis()
        val separator = preferences.mergeSeparator()
        val body = selected.joinToString(separator) { it.body }
        val title = selected.first().title.let { base ->
            if (selected.size == 1) base else "$base + ${selected.size - 1}"
        }
        val position = nextPosition()
        return notes.mergeIntoNew(
            title = title,
            body = body,
            categoryId = selected.first().categoryId ?: defaultCategoryId(),
            position = position,
            now = now,
            trashSourceIds = if (trashSources) selected.map { it.id } else emptyList(),
        )
    }

    suspend fun addCategory(name: String): Long {
        val clean = name.trim().ifBlank { "Untitled" }
        return categories.insert(
            CategoryEntity(name = clean, isDefault = false, position = categories.maxPosition() + 1),
        )
    }

    suspend fun renameCategory(id: Long, name: String) {
        val existing = categories.getById(id) ?: return
        categories.update(existing.copy(name = name.trim().ifBlank { existing.name }))
    }

    suspend fun deleteCategory(id: Long) {
        val existing = categories.getById(id) ?: return
        if (existing.isDefault) return
        val fallback = categories.getDefault()?.id
        val now = System.currentTimeMillis()
        if (fallback != null) notes.reassignCategory(id, fallback, now)
        categories.deleteIfNotDefault(id)
    }

    suspend fun mergeCategories(sourceIds: List<Long>, targetId: Long) {
        categories.mergeInto(sourceIds.filter { it != targetId }, targetId, notes, System.currentTimeMillis())
    }

    suspend fun exportJson(): String = buildExport(categories.getAll())

    private suspend fun buildExport(cat: List<CategoryEntity>): String {
        val noteList = notes.snapshot()
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "ClipVault")
        val catArr = JSONArray()
        cat.forEach { c ->
            catArr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("isDefault", c.isDefault)
                    .put("position", c.position),
            )
        }
        val noteArr = JSONArray()
        noteList.forEach { n ->
            noteArr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("title", n.title)
                    .put("body", n.body)
                    .put("categoryId", n.categoryId ?: JSONObject.NULL)
                    .put("isFavorite", n.isFavorite)
                    .put("isTrash", n.isTrash)
                    .put("position", n.position)
                    .put("createdAt", n.createdAt)
                    .put("updatedAt", n.updatedAt)
                    .put("trashedAt", n.trashedAt ?: JSONObject.NULL),
            )
        }
        root.put("categories", catArr)
        root.put("notes", noteArr)
        return root.toString(2)
    }

    /**
     * Replaces local categories and notes with the file contents.
     * Ids from the file are preserved so relations stay intact.
     */
    suspend fun importJson(raw: String) {
        val root = JSONObject(raw)
        val catArr = root.getJSONArray("categories")
        val noteArr = root.getJSONArray("notes")
        val importedCategories = buildList {
            for (i in 0 until catArr.length()) {
                val o = catArr.getJSONObject(i)
                add(
                    CategoryEntity(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        isDefault = o.optBoolean("isDefault"),
                        position = o.optInt("position"),
                    ),
                )
            }
        }
        val importedNotes = buildList {
            for (i in 0 until noteArr.length()) {
                val o = noteArr.getJSONObject(i)
                add(
                    NoteEntity(
                        id = o.getLong("id"),
                        title = o.optString("title"),
                        body = o.optString("body"),
                        categoryId = if (o.isNull("categoryId")) null else o.getLong("categoryId"),
                        isFavorite = o.optBoolean("isFavorite"),
                        isTrash = o.optBoolean("isTrash"),
                        position = o.optInt("position"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        trashedAt = if (o.isNull("trashedAt")) null else o.optLong("trashedAt"),
                    ),
                )
            }
        }
        db.importSnapshot(importedCategories, importedNotes)
    }

    private suspend fun nextPosition(): Int = notes.maxActivePosition() + 1

    private fun sort(list: List<NoteEntity>, order: NotesOrder): List<NoteEntity> = when (order) {
        NotesOrder.MANUAL -> list.sortedWith(compareBy<NoteEntity> { it.position }.thenByDescending { it.createdAt })
        NotesOrder.NEWEST -> list.sortedByDescending { it.createdAt }
        NotesOrder.OLDEST -> list.sortedBy { it.createdAt }
        NotesOrder.ALPHA -> list.sortedBy { it.title.lowercase() }
    }

    companion object {
        fun deriveTitle(body: String): String {
            val line = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (line.isEmpty()) return "Untitled"
            return if (line.length <= 48) line else line.take(48).trimEnd() + "…"
        }
    }
}
